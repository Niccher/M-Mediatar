package com.nicch.mediatar;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.net.URL;

public class Exporter {

    private static final java.util.Set<String> AUDIO_EXTS = new java.util.HashSet<>(
        java.util.Arrays.asList(".mp3", ".wav", ".aac", ".m4a", ".flac", ".ogg", ".wma")
    );

    public static void exportTimeline(List<MainWorkhorse.TimelineTrack> tracks, ExportConfig config) {
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                File tempDir = Files.createTempDirectory("mediatar_export").toFile();
                File audioThumbnail = getAudioThumbnailPath();

                boolean audioOnlyOutput = config.isAudioOnlyFormat();
                File v1Master = null;

                // Phase 1: Process V1 as the primary video track
                List<MainWorkhorse.TimelineTrack> videoTracks = tracks.stream().filter(t -> t.getType() == MainWorkhorse.TrackType.VIDEO).collect(java.util.stream.Collectors.toList());
                MainWorkhorse.TimelineTrack v1 = videoTracks.size() > 0 ? videoTracks.get(0) : null;
                MainWorkhorse.TimelineTrack v2 = videoTracks.size() > 1 ? videoTracks.get(1) : null;

                if (!audioOnlyOutput && v1 != null && !v1.getClips().isEmpty()) {
                    v1Master = processVideoTrack(v1, tempDir, "v1", config, audioThumbnail);
                }

                // Gather all audio tracks (A1/A2)
                List<File> audioMasters = new ArrayList<>();
                for (int i = 0; i < tracks.size(); i++) {
                    MainWorkhorse.TimelineTrack track = tracks.get(i);
                    if (track != null && track.getType() == MainWorkhorse.TrackType.AUDIO && !track.getClips().isEmpty()) {
                        String prefix = "track_" + i;
                        File aMaster = processAudioTrack(track, tempDir, prefix, config, audioThumbnail);
                        if (aMaster != null && aMaster.exists()) {
                            audioMasters.add(aMaster);
                        }
                    }
                }
                
                // Extract audio from V2 clips so they mix correctly in the final output
                if (v2 != null && !v2.getClips().isEmpty()) {
                    File v2AudioMaster = processAudioTrack(v2, tempDir, "v2_audio", config, audioThumbnail);
                    if (v2AudioMaster != null && v2AudioMaster.exists()) {
                        audioMasters.add(v2AudioMaster);
                    }
                }

                // Phase 4: Final Mixdown
                List<String> mixCmd = new ArrayList<>();
                mixCmd.add("ffmpeg");
                mixCmd.add("-hide_banner");
                mixCmd.add("-y");

                if (v1Master != null) {
                    mixCmd.add("-i"); mixCmd.add(v1Master.getAbsolutePath());
                }
                for (File a : audioMasters) {
                    mixCmd.add("-i"); mixCmd.add(a.getAbsolutePath());
                }

                if (v1Master == null && audioMasters.isEmpty()) {
                    throw new Exception("Timeline is empty — nothing to render!");
                }

                // Build audio mixing
                List<String> audioLabels = new ArrayList<>();
                if (v1Master != null) {
                    audioLabels.add("[0:a]"); // merged video track embedded audio
                }
                for (int i = 0; i < audioMasters.size(); i++) {
                    int inputIdx = (v1Master != null ? 1 : 0) + i;
                    audioLabels.add("[" + inputIdx + ":a]");
                }

                int firstAudioIdx = (v1Master != null) ? 1 : 0;
                boolean hasVideoOutput = !config.isAudioOnlyFormat()
                        && (v1Master != null || !audioMasters.isEmpty());

                if (audioLabels.size() > 1) {
                    StringBuilder filterComplex = new StringBuilder();
                    for (String label : audioLabels) filterComplex.append(label);
                    filterComplex.append("amix=inputs=").append(audioLabels.size())
                                 .append(":duration=longest:dropout_transition=3[aout]");
                    mixCmd.add("-filter_complex"); mixCmd.add(filterComplex.toString());
                    if (v1Master != null) {
                        mixCmd.add("-map"); mixCmd.add("0:v");
                    } else if (hasVideoOutput) {
                        mixCmd.add("-map"); mixCmd.add(firstAudioIdx + ":v");
                    }
                    mixCmd.add("-map"); mixCmd.add("[aout]");
                } else if (audioLabels.size() == 1) {
                    if (v1Master != null) {
                        mixCmd.add("-map"); mixCmd.add("0:v");
                        mixCmd.add("-map"); mixCmd.add("0:a?");
                    } else {
                        if (hasVideoOutput) { mixCmd.add("-map"); mixCmd.add(firstAudioIdx + ":v"); }
                        mixCmd.add("-map"); mixCmd.add(firstAudioIdx + ":a");
                    }
                } else {
                    if (v1Master != null) { mixCmd.add("-map"); mixCmd.add("0:v"); }
                }

                // Codec settings — only re-encode if text overlay needed
                MainWorkhorse.TimelineTrack t1 = tracks.stream().filter(t -> t.getType() == MainWorkhorse.TrackType.TEXT).findFirst().orElse(null);
                boolean hasText = t1 != null && !t1.getClips().isEmpty();

                if (hasVideoOutput) {
                    mixCmd.add("-c:v"); mixCmd.add(hasText ? "copy" : config.getVideoCodec());
                    if (!hasText) { mixCmd.add("-b:v"); mixCmd.add(config.getVideoBitrate()); mixCmd.add("-preset"); mixCmd.add("fast"); }
                }
                mixCmd.add("-c:a"); mixCmd.add(config.getAudioCodec());
                mixCmd.add("-b:a"); mixCmd.add(config.getAudioBitrate());

                boolean needsOverlay = hasVideoOutput && (hasText || (v2 != null && !v2.getClips().isEmpty()));
                File mixOut = needsOverlay ? new File(tempDir, "mixed_pre_overlay.mp4") : config.getDestinationFile();
                mixCmd.add(mixOut.getAbsolutePath());

                config.getCallback().onLog("\n▶ Final Mixdown...");
                runProcess(mixCmd, config);

                // Phase 5: Overlays (V2 and Text)
                if (needsOverlay) {
                    File textOut = processOverlayPass(t1, v2, mixOut, tempDir, "overlay", config);
                    java.nio.file.Files.copy(textOut.toPath(), config.getDestinationFile().toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }

                // Cleanup temp files
                config.getCallback().onLog("🧹 Cleaning up temporary files...");
                File[] files = tempDir.listFiles();
                if (files != null) for (File f : files) f.delete();
                tempDir.delete();

                long endTime = System.currentTimeMillis();
                long durationSec = (endTime - startTime) / 1000;
                config.getCallback().onLog(String.format("\n✅ Export completed successfully in %d seconds.", durationSec));

                config.getCallback().onComplete(true, config.getDestinationFile());

            } catch (Exception e) {
                e.printStackTrace();
                config.getCallback().onLog("🔴 ERROR: " + e.getMessage());
                config.getCallback().onComplete(false, null);
            }
        }).start();
    }

    // ─── Overlay Track Processing ────────────────────────────────────────────────
    private static File processOverlayPass(MainWorkhorse.TimelineTrack t1, MainWorkhorse.TimelineTrack v2, File v1Master, File tempDir, String prefix, ExportConfig config) throws Exception {
        config.getCallback().onLog("\n── Applying Overlays (PiP / Text) ──");
        File textOut = new File(tempDir, prefix + "_master.mp4");
        
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg"); cmd.add("-hide_banner"); cmd.add("-y");
        cmd.add("-i"); cmd.add(v1Master.getAbsolutePath());
        
        List<File> v2ProcessedClips = new ArrayList<>();
        if (v2 != null && !v2.getClips().isEmpty()) {
            int clipIdx = 0;
            for (MainWorkhorse.TimelineClip clip : v2.getClips()) {
                boolean srcIsAudio = isAudioOnlyFile(clip.getMediaItem().getFile().getName());
                // Use processVideoClip instead of processPiPClip so V2 gets the same aspect ratio/padding as V1
                File processed = srcIsAudio
                    ? processAudioAsVideoClip(clip, tempDir, "v2_pip_" + clipIdx, config, null)
                    : processVideoClip(clip, tempDir, "v2_pip_" + clipIdx, config);
                v2ProcessedClips.add(processed);
                // itsoffset shifts the input PTS by +startSeconds so the clip
                // lines up with the global timeline inside filter_complex
                cmd.add("-itsoffset"); cmd.add(String.valueOf(clip.getStartSeconds()));
                cmd.add("-i"); cmd.add(processed.getAbsolutePath());
                clipIdx++;
            }
        }
        
        StringBuilder filter = new StringBuilder();
        String currentPad = "[0:v]";
        int inputIdx = 1;
        int v2Idx = 0;

        if (!v2ProcessedClips.isEmpty()) {
            for (MainWorkhorse.TimelineClip clip : v2.getClips()) {
                String inPad = "[" + inputIdx + ":v]";
                String scalePad = "[v2_scale_" + v2Idx + "]";
                String outPad = "[ov_" + v2Idx + "]";
                
                double s = clip.getScale();
                filter.append(inPad).append("scale=iw*").append(s).append(":ih*").append(s).append(scalePad).append(";");
                
                String xExpr = "(W/2)+(" + clip.getPosX() + "*3)-(w/2)";
                String yExpr = "(H/2)+(" + clip.getPosY() + "*3)-(h/2)";
                
                double start = clip.getStartSeconds();
                double end = start + clip.getDurationSeconds();
                filter.append(currentPad).append(scalePad).append("overlay=x='").append(xExpr).append("':y='").append(yExpr)
                      .append("':enable='between(t,").append(start).append(",").append(end).append(")'").append(outPad).append(";");
                
                currentPad = outPad;
                inputIdx++;
                v2Idx++;
            }
        }
        
        boolean hasText = false;
        StringBuilder textFilters = new StringBuilder();
        if (t1 != null && !t1.getClips().isEmpty()) {
            int clipIdx = 0;
            for (MainWorkhorse.TimelineClip clip : t1.getClips()) {
                if (clip.getTextContent() == null || clip.getTextContent().trim().isEmpty()) continue;
                hasText = true;
                File textFile = new File(tempDir, prefix + "_text_" + clipIdx + ".txt");
                java.nio.file.Files.writeString(textFile.toPath(), clip.getTextContent());
                
                String color = clip.getTextColor().replace("#", "0x");
                int size = clip.getFontSize();
                double start = clip.getStartSeconds();
                double end = start + clip.getDurationSeconds();
                
                String escapedPath = textFile.getAbsolutePath().replace("\\", "/").replace(":", "\\:").replace("'", "\\'");
                
                // Find a usable font — fall back through common Linux locations
                String fontFile = findFontFile();
                String fontOption = fontFile != null ? ":fontfile='" + fontFile + "'" : "";
                
                if (textFilters.length() > 0) textFilters.append(",");
                textFilters.append(String.format(
                    "drawtext=textfile='%s'%s:fontcolor=%s:fontsize=%d:x=(w-text_w)/2:y=(h-text_h)/2:shadowcolor=black@0.8:shadowx=3:shadowy=3:enable='between(t,%.3f,%.3f)'",
                    escapedPath, fontOption, color, size, start, end));
                clipIdx++;
            }
        }

        if (v2ProcessedClips.isEmpty() && !hasText) return v1Master;
        
        if (v2ProcessedClips.isEmpty() && hasText) {
            // Text-only
            cmd.add("-vf"); cmd.add(textFilters.toString());
            cmd.add("-map"); cmd.add("0:v"); // Required when other streams are explicitly mapped
            cmd.add("-map"); cmd.add("0:a?");
        } else if (!v2ProcessedClips.isEmpty() && !hasText) {
            filter.setLength(filter.length() - 1);
            cmd.add("-filter_complex"); cmd.add(filter.toString());
            cmd.add("-map"); cmd.add(currentPad);
            cmd.add("-map"); cmd.add("0:a?");
        } else if (!v2ProcessedClips.isEmpty() && hasText) {
            filter.append(currentPad).append(textFilters.toString()).append("[outv]");
            cmd.add("-filter_complex"); cmd.add(filter.toString());
            cmd.add("-map"); cmd.add("[outv]");
            cmd.add("-map"); cmd.add("0:a?");
        }
        
        cmd.add("-c:a"); cmd.add("copy");
        cmd.add("-c:v"); cmd.add(config.getVideoCodec());
        cmd.add("-b:v"); cmd.add(config.getVideoBitrate());
        cmd.add("-preset"); cmd.add("fast");
        cmd.add(textOut.getAbsolutePath());
        
        runProcess(cmd, config);
        return textOut;
    }

    // ─── Video Track Processing ────────────────────────────────────────────────
    private static File processVideoTrack(MainWorkhorse.TimelineTrack track, File tempDir,
                                          String prefix, ExportConfig config, File audioThumbnail) throws Exception {
        config.getCallback().onLog("\n── Processing Video Track: " + prefix.toUpperCase() + " ──");
        List<MainWorkhorse.TimelineClip> clips = new ArrayList<>(track.getClips());
        clips.sort(Comparator.comparingDouble(c -> c.getStartSeconds()));

        File concatList = new File(tempDir, prefix + "_concat.txt");
        PrintWriter writer = new PrintWriter(concatList);

        double currentTime = 0;
        int clipIdx = 0;

        for (MainWorkhorse.TimelineClip clip : clips) {
            if (clip.getStartSeconds() > currentTime + 0.001) {
                double gapDuration = clip.getStartSeconds() - currentTime;
                config.getCallback().onLog("  ⬛ Generating " + String.format("%.2f", gapDuration) + "s black gap...");
                File gapFile = generateBlankVideo(tempDir, prefix + "_gap_" + clipIdx, gapDuration, config);
                writer.println("file '" + gapFile.getAbsolutePath() + "'");
            }

            config.getCallback().onLog("  🎬 Rendering: " + clip.getAliasName());
            boolean srcIsAudio = isAudioOnlyFile(clip.getMediaItem().getFile().getName());
            File processedClip = srcIsAudio
                ? processAudioAsVideoClip(clip, tempDir, prefix + "_clip_" + clipIdx, config, audioThumbnail)
                : processVideoClip(clip, tempDir, prefix + "_clip_" + clipIdx, config);
            writer.println("file '" + processedClip.getAbsolutePath() + "'");

            currentTime = clip.getStartSeconds() + (clip.getDurationSeconds() / clip.getPlaybackSpeed());
            clipIdx++;
        }
        writer.close();

        File masterFile = new File(tempDir, prefix + "_master.mp4");
        config.getCallback().onLog("  📎 Concatenating " + clipIdx + " segment(s) for " + prefix.toUpperCase());
        List<String> cmd = buildConcatCmd(concatList, masterFile);
        runProcess(cmd, config);
        return masterFile;
    }

    // ─── Audio Track Processing ────────────────────────────────────────────────
    // Audio tracks now produce .mp4 with the thumbnail image as video so the
    // thumbnail shows in the final output whenever only audio is playing.
    private static File processAudioTrack(MainWorkhorse.TimelineTrack track, File tempDir,
                                          String prefix, ExportConfig config, File audioThumbnail) throws Exception {
        config.getCallback().onLog("\n── Processing Audio Track: " + prefix.toUpperCase() + " ──");
        List<MainWorkhorse.TimelineClip> clips = new ArrayList<>(track.getClips());
        clips.sort(Comparator.comparingDouble(c -> c.getStartSeconds()));

        File concatList = new File(tempDir, prefix + "_concat.txt");
        PrintWriter writer = new PrintWriter(concatList);

        double currentTime = 0;
        int clipIdx = 0;

        for (MainWorkhorse.TimelineClip clip : clips) {
            if (clip.getStartSeconds() > currentTime + 0.001) {
                double gapDuration = clip.getStartSeconds() - currentTime;
                config.getCallback().onLog("  🔇 Generating " + String.format("%.2f", gapDuration) + "s thumbnail gap...");
                File gapFile = generateAudioBackgroundVideo(tempDir, prefix + "_gap_" + clipIdx, gapDuration, config, audioThumbnail);
                writer.println("file '" + gapFile.getAbsolutePath() + "'");
            }

            config.getCallback().onLog("  🔊 Rendering audio: " + clip.getAliasName());
            File processedClip = processAudioAsVideoClip(clip, tempDir, prefix + "_clip_" + clipIdx, config, audioThumbnail);
            writer.println("file '" + processedClip.getAbsolutePath() + "'");

            currentTime = clip.getStartSeconds() + (clip.getDurationSeconds() / clip.getPlaybackSpeed());
            clipIdx++;
        }
        writer.close();

        File masterFile = new File(tempDir, prefix + "_master.mp4");
        config.getCallback().onLog("  📎 Concatenating audio track segments...");
        List<String> cmd = buildConcatCmd(concatList, masterFile);
        runProcess(cmd, config);
        return masterFile;
    }

    // ─── Individual Clip Processors ───────────────────────────────────────────
    // processPiPClip removed: V2 now uses processVideoClip to ensure identical padding to V1

    private static File processVideoClip(MainWorkhorse.TimelineClip clip, File tempDir,
                                         String name, ExportConfig config) throws Exception {
        File out = new File(tempDir, name + ".mp4");
        double speed = clip.getPlaybackSpeed();
        String res = config.getResolution().replace("x", ":");

        // Probe once so we can build the correct filter_complex ("[0:a?]" is invalid inside filtergraphs)
        boolean srcHasAudio = hasAudioStream(clip.getMediaItem().getFile()) && !clip.isMuted();

        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg"); cmd.add("-hide_banner"); cmd.add("-y");
        cmd.add("-ss"); cmd.add(String.valueOf(clip.getMediaStartOffset()));
        cmd.add("-t"); cmd.add(String.valueOf(clip.getDurationSeconds()));
        cmd.add("-i"); cmd.add(clip.getMediaItem().getFile().getAbsolutePath());
        // Always add a silent fallback as input [1] for clips without audio
        cmd.add("-f"); cmd.add("lavfi");
        cmd.add("-i"); cmd.add("anullsrc=r=48000:cl=stereo");

        String vFilter = "scale=" + res + ":force_original_aspect_ratio=decrease,pad=" + res + ":(ow-iw)/2:(oh-ih)/2,fps=" + config.getFramerate() + ",format=yuv420p";
        if (speed != 1.0) vFilter += ",setpts=" + (1.0 / speed) + "*PTS";

        String aFilter = "atempo=" + clampAtempo(speed) + ",aformat=sample_rates=48000:channel_layouts=stereo";
        // Use real audio if present, else fall back to silent anullsrc [1:a]
        String filterComplex = srcHasAudio
                ? "[0:v]" + vFilter + "[v];[0:a]" + aFilter + "[a]"
                : "[0:v]" + vFilter + "[v];[1:a]aformat=sample_rates=48000:channel_layouts=stereo[a]";

        cmd.add("-filter_complex"); cmd.add(filterComplex);
        cmd.add("-map"); cmd.add("[v]");
        cmd.add("-map"); cmd.add("[a]");
        cmd.add("-c:v"); cmd.add(config.getVideoCodec());
        cmd.add("-b:v"); cmd.add(config.getVideoBitrate());
        cmd.add("-preset"); cmd.add("fast");
        cmd.add("-c:a"); cmd.add("aac");
        cmd.add("-b:a"); cmd.add(config.getAudioBitrate());
        cmd.add(out.getAbsolutePath());
        runProcess(cmd, config);
        return out;
    }

    private static File processAudioAsVideoClip(MainWorkhorse.TimelineClip clip, File tempDir,
                                                 String name, ExportConfig config, File thumbnail) throws Exception {
        File out = new File(tempDir, name + ".mp4");
        String res = config.getResolution().replace("x", ":");

        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg"); cmd.add("-hide_banner"); cmd.add("-y");
        // Loop the thumbnail image as video source
        cmd.add("-loop"); cmd.add("1");
        cmd.add("-framerate"); cmd.add(String.valueOf(config.getFramerate()));
        if (thumbnail != null && thumbnail.exists()) {
            cmd.add("-i"); cmd.add(thumbnail.getAbsolutePath());
        } else {
            cmd.add("-f"); cmd.add("lavfi");
            cmd.add("-i"); cmd.add("color=c=#004455:s=" + config.getResolution() + ":r=" + config.getFramerate());
        }
        // Add actual audio
        cmd.add("-ss"); cmd.add(String.valueOf(clip.getMediaStartOffset()));
        cmd.add("-t"); cmd.add(String.valueOf(clip.getDurationSeconds()));
        cmd.add("-i"); cmd.add(clip.getMediaItem().getFile().getAbsolutePath());

        String vFilter = "scale=" + res + ":force_original_aspect_ratio=decrease,pad=" + res + ":(ow-iw)/2:(oh-ih)/2,fps=" + config.getFramerate() + ",format=yuv420p";
        double speed = clip.getPlaybackSpeed();
        String aFilter = "atempo=" + clampAtempo(speed) + ",aformat=sample_rates=48000:channel_layouts=stereo";

        cmd.add("-filter_complex"); cmd.add("[0:v]" + vFilter + "[v];[1:a]" + aFilter + "[a]");
        cmd.add("-map"); cmd.add("[v]");
        cmd.add("-map"); cmd.add("[a]");
        cmd.add("-t"); cmd.add(String.valueOf(clip.getDurationSeconds()));
        cmd.add("-c:v"); cmd.add(config.getVideoCodec());
        cmd.add("-b:v"); cmd.add(config.getVideoBitrate());
        cmd.add("-preset"); cmd.add("fast");
        cmd.add("-c:a"); cmd.add("aac");
        cmd.add("-b:a"); cmd.add(config.getAudioBitrate());
        cmd.add(out.getAbsolutePath());
        runProcess(cmd, config);
        return out;
    }

    private static File processAudioClip(MainWorkhorse.TimelineClip clip, File tempDir,
                                          String name, ExportConfig config) throws Exception {
        File out = new File(tempDir, name + ".m4a");
        double speed = clip.getPlaybackSpeed();
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg"); cmd.add("-hide_banner"); cmd.add("-y");
        cmd.add("-ss"); cmd.add(String.valueOf(clip.getMediaStartOffset()));
        cmd.add("-t"); cmd.add(String.valueOf(clip.getDurationSeconds()));
        cmd.add("-i"); cmd.add(clip.getMediaItem().getFile().getAbsolutePath());
        cmd.add("-filter:a"); cmd.add("atempo=" + clampAtempo(speed) + ",aformat=sample_rates=48000:channel_layouts=stereo");
        cmd.add("-c:a"); cmd.add("aac");
        cmd.add("-b:a"); cmd.add(config.getAudioBitrate());
        cmd.add(out.getAbsolutePath());
        runProcess(cmd, config);
        return out;
    }

    // ─── Gap Generators ───────────────────────────────────────────────────────
    private static File generateBlankVideo(File tempDir, String name, double duration, ExportConfig config) throws Exception {
        File out = new File(tempDir, name + ".mp4");
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg"); cmd.add("-hide_banner"); cmd.add("-y");
        cmd.add("-f"); cmd.add("lavfi");
        cmd.add("-i"); cmd.add("color=c=black:s=" + config.getResolution() + ":r=" + config.getFramerate());
        cmd.add("-f"); cmd.add("lavfi");
        cmd.add("-i"); cmd.add("anullsrc=r=48000:cl=stereo");
        cmd.add("-t"); cmd.add(String.valueOf(duration));
        cmd.add("-c:v"); cmd.add(config.getVideoCodec());
        cmd.add("-preset"); cmd.add("fast");
        cmd.add("-c:a"); cmd.add("aac");
        cmd.add(out.getAbsolutePath());
        runProcess(cmd, config);
        return out;
    }

    private static File generateSilence(File tempDir, String name, double duration, ExportConfig config) throws Exception {
        File out = new File(tempDir, name + ".m4a");
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg"); cmd.add("-hide_banner"); cmd.add("-y");
        cmd.add("-f"); cmd.add("lavfi");
        cmd.add("-i"); cmd.add("anullsrc=r=48000:cl=stereo");
        cmd.add("-t"); cmd.add(String.valueOf(duration));
        cmd.add("-c:a"); cmd.add("aac");
        cmd.add(out.getAbsolutePath());
        runProcess(cmd, config);
        return out;
    }

    /** Generates a video segment using the audio thumbnail image (or dark fallback) with silent audio.
     *  Used to fill gaps between clips on audio tracks so all segments are .mp4 for concat. */
    private static File generateAudioBackgroundVideo(File tempDir, String name, double duration,
                                                     ExportConfig config, File thumbnail) throws Exception {
        File out = new File(tempDir, name + ".mp4");
        String res = config.getResolution().replace("x", ":");
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg"); cmd.add("-hide_banner"); cmd.add("-y");
        cmd.add("-loop"); cmd.add("1");
        cmd.add("-framerate"); cmd.add(String.valueOf(config.getFramerate()));
        if (thumbnail != null && thumbnail.exists()) {
            cmd.add("-i"); cmd.add(thumbnail.getAbsolutePath());
        } else {
            cmd.add("-f"); cmd.add("lavfi");
            cmd.add("-i"); cmd.add("color=c=#1a1a2e:s=" + config.getResolution() + ":r=" + config.getFramerate());
        }
        cmd.add("-f"); cmd.add("lavfi");
        cmd.add("-i"); cmd.add("anullsrc=r=48000:cl=stereo");
        String vFilter = "scale=" + res + ":force_original_aspect_ratio=decrease,pad=" + res
                + ":(ow-iw)/2:(oh-ih)/2,fps=" + config.getFramerate() + ",format=yuv420p";
        cmd.add("-filter_complex");
        cmd.add("[0:v]" + vFilter + "[v];[1:a]aformat=sample_rates=48000:channel_layouts=stereo[a]");
        cmd.add("-map"); cmd.add("[v]");
        cmd.add("-map"); cmd.add("[a]");
        cmd.add("-t"); cmd.add(String.valueOf(duration));
        cmd.add("-c:v"); cmd.add(config.getVideoCodec());
        cmd.add("-b:v"); cmd.add(config.getVideoBitrate());
        cmd.add("-preset"); cmd.add("fast");
        cmd.add("-c:a"); cmd.add("aac");
        cmd.add("-b:a"); cmd.add(config.getAudioBitrate());
        cmd.add(out.getAbsolutePath());
        runProcess(cmd, config);
        return out;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private static List<String> buildConcatCmd(File concatList, File masterFile) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg"); cmd.add("-hide_banner"); cmd.add("-y");
        cmd.add("-f"); cmd.add("concat");
        cmd.add("-safe"); cmd.add("0");
        cmd.add("-i"); cmd.add(concatList.getAbsolutePath());
        cmd.add("-c"); cmd.add("copy");
        cmd.add(masterFile.getAbsolutePath());
        return cmd;
    }


    /** Returns true if the given file contains at least one audio stream. */
    private static boolean hasAudioStream(File file) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-hide_banner", "-loglevel", "error",
                "-select_streams", "a",
                "-show_entries", "stream=codec_type",
                "-of", "default=noprint_wrappers=1",
                file.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            return !output.isEmpty();
        } catch (Exception e) {
            return false; // assume no audio if probe fails
        }
    }

    private static boolean isAudioOnlyFile(String filename) {
        String lower = filename.toLowerCase();
        for (String ext : AUDIO_EXTS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private static double clampAtempo(double speed) {
        // atempo only accepts 0.5–100.0; chain filters for extremes
        return Math.max(0.5, Math.min(100.0, speed));
    }

    private static File getAudioThumbnailPath() {
        try {
            File hardcoded = new File("/home/domino/NetBeansProjects/Mediatar/src/main/resources/com/nicch/mediatar/audio_thumbnail.png");
            if (hardcoded.exists()) {
                return hardcoded;
            }
            java.io.InputStream is = Exporter.class.getResourceAsStream("/com/nicch/mediatar/audio_thumbnail.png");
            if (is != null) {
                File temp = File.createTempFile("audio_thumbnail", ".png");
                temp.deleteOnExit();
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(temp)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                is.close();
                return temp;
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
        return null;
    }

    /** Finds a usable TTF/OTF font for drawtext. Returns null if none found. */
    private static String findFontFile() {
        String[] candidates = {
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
            "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
            "/usr/share/fonts/TTF/DejaVuSans.ttf",
            "/usr/share/fonts/dejavu/DejaVuSans.ttf"
        };
        for (String path : candidates) {
            if (new File(path).exists()) return path;
        }
        // Try fc-match as last resort
        try {
            Process p = new ProcessBuilder("fc-match", "--format=%{file}", "sans").start();
            String result = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            if (!result.isEmpty() && new File(result).exists()) return result;
        } catch (Exception ignored) {}
        return null;
    }

    private static void runProcess(List<String> cmd, ExportConfig config) throws Exception {
        config.getCallback().onLog("> " + String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            long lastUpdate = System.currentTimeMillis();
            StringBuilder lastFrame = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                long now = System.currentTimeMillis();
                if (line.startsWith("frame=")) {
                    // Throttle frame progress to every 500ms to prevent UI lockup
                    lastFrame = new StringBuilder(line);
                    if (now - lastUpdate > 500) {
                        config.getCallback().onLog("  " + lastFrame);
                        lastUpdate = now;
                    }
                } else {
                    config.getCallback().onLog(line);
                }
            }
            // Always log the last frame update
            if (lastFrame.length() > 0) config.getCallback().onLog("  " + lastFrame);
        }

        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new Exception("ffmpeg process exited with code " + exitCode + ". Check the log above.");
        }
    }
}
