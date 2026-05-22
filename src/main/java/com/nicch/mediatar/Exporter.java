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
            try {
                File tempDir = Files.createTempDirectory("mediatar_export").toFile();
                File audioThumbnail = getAudioThumbnailPath();

                boolean audioOnlyOutput = config.isAudioOnlyFormat();
                File v1Master = null;

                // Phase 1 & 2: Process V1 (video track)
                if (!audioOnlyOutput) {
                    MainWorkhorse.TimelineTrack v1 = tracks.size() > 0 ? tracks.get(0) : null;
                    if (v1 != null && !v1.getClips().isEmpty()) {
                        v1Master = processVideoTrack(v1, tempDir, "v1", config, audioThumbnail);
                    }
                }

                // Gather all audio tracks (both V1 audio and A1/A2)
                List<File> audioMasters = new ArrayList<>();
                for (int i = audioOnlyOutput ? 0 : 1; i < tracks.size(); i++) {
                    MainWorkhorse.TimelineTrack track = tracks.get(i);
                    if (track != null && !track.getClips().isEmpty()) {
                        String prefix = "track_" + i;
                        File aMaster = processAudioTrack(track, tempDir, prefix, config);
                        if (aMaster != null && aMaster.exists()) {
                            audioMasters.add(aMaster);
                        }
                    }
                }

                // Phase 4: Final Mixdown
                List<String> mixCmd = new ArrayList<>();
                mixCmd.add("ffmpeg");
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

                // Build audio mixing — always include V1's embedded audio when V1 exists
                // Total audio streams = v1 audio (if present) + all explicit audio track files
                List<String> audioLabels = new ArrayList<>();
                if (v1Master != null) {
                    audioLabels.add("[0:a]"); // V1's embedded audio
                }
                for (int i = 0; i < audioMasters.size(); i++) {
                    // Inputs are: index 0=v1Master (if present), then audioMasters
                    int inputIdx = (v1Master != null ? 1 : 0) + i;
                    audioLabels.add("[" + inputIdx + ":a]");
                }

                if (audioLabels.size() > 1) {
                    // Multiple audio sources — mix them with amix
                    StringBuilder filterComplex = new StringBuilder();
                    for (String label : audioLabels) filterComplex.append(label);
                    filterComplex.append("amix=inputs=").append(audioLabels.size())
                                 .append(":duration=first:dropout_transition=3[aout]");
                    mixCmd.add("-filter_complex"); mixCmd.add(filterComplex.toString());
                    if (v1Master != null) { mixCmd.add("-map"); mixCmd.add("0:v"); }
                    mixCmd.add("-map"); mixCmd.add("[aout]");
                } else if (audioLabels.size() == 1) {
                    // Single audio source — map it directly
                    if (v1Master != null) {
                        mixCmd.add("-map"); mixCmd.add("0:v");
                        mixCmd.add("-map"); mixCmd.add("0:a?");
                    } else {
                        mixCmd.add("-map"); mixCmd.add("0:a");
                    }
                } else {
                    // No audio at all — map video only
                    if (v1Master != null) { mixCmd.add("-map"); mixCmd.add("0:v"); }
                }

                // Codec settings
                if (v1Master != null && !config.isAudioOnlyFormat()) {
                    mixCmd.add("-c:v"); mixCmd.add("copy");
                }
                mixCmd.add("-c:a"); mixCmd.add(config.getAudioCodec());
                mixCmd.add("-b:a"); mixCmd.add(config.getAudioBitrate());
                mixCmd.add(config.getDestinationFile().getAbsolutePath());

                config.getCallback().onLog("\n▶ Final Mixdown...");
                runProcess(mixCmd, config);

                // Cleanup temp files
                config.getCallback().onLog("🧹 Cleaning up temporary files...");
                File[] files = tempDir.listFiles();
                if (files != null) for (File f : files) f.delete();
                tempDir.delete();

                config.getCallback().onComplete(true, config.getDestinationFile());

            } catch (Exception e) {
                e.printStackTrace();
                config.getCallback().onLog("🔴 ERROR: " + e.getMessage());
                config.getCallback().onComplete(false, null);
            }
        }).start();
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
    private static File processAudioTrack(MainWorkhorse.TimelineTrack track, File tempDir,
                                          String prefix, ExportConfig config) throws Exception {
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
                config.getCallback().onLog("  🔇 Generating " + String.format("%.2f", gapDuration) + "s silence...");
                File silenceFile = generateSilence(tempDir, prefix + "_silence_" + clipIdx, gapDuration, config);
                writer.println("file '" + silenceFile.getAbsolutePath() + "'");
            }

            config.getCallback().onLog("  🔊 Rendering audio: " + clip.getAliasName());
            File processedClip = processAudioClip(clip, tempDir, prefix + "_clip_" + clipIdx, config);
            writer.println("file '" + processedClip.getAbsolutePath() + "'");

            currentTime = clip.getStartSeconds() + (clip.getDurationSeconds() / clip.getPlaybackSpeed());
            clipIdx++;
        }
        writer.close();

        File masterFile = new File(tempDir, prefix + "_master.m4a");
        config.getCallback().onLog("  📎 Concatenating audio segments...");
        List<String> cmd = buildConcatCmd(concatList, masterFile);
        runProcess(cmd, config);
        return masterFile;
    }

    // ─── Individual Clip Processors ───────────────────────────────────────────
    private static File processVideoClip(MainWorkhorse.TimelineClip clip, File tempDir,
                                         String name, ExportConfig config) throws Exception {
        File out = new File(tempDir, name + ".mp4");
        double speed = clip.getPlaybackSpeed();
        String res = config.getResolution().replace("x", ":");

        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg"); cmd.add("-y");
        cmd.add("-ss"); cmd.add(String.valueOf(clip.getMediaStartOffset()));
        cmd.add("-t"); cmd.add(String.valueOf(clip.getDurationSeconds()));
        cmd.add("-i"); cmd.add(clip.getMediaItem().getFile().getAbsolutePath());

        String vFilter = "scale=" + res + ":force_original_aspect_ratio=decrease,pad=" + res + ":(ow-iw)/2:(oh-ih)/2,fps=" + config.getFramerate();
        if (speed != 1.0) vFilter += ",setpts=" + (1.0 / speed) + "*PTS";

        String aFilter = "atempo=" + clampAtempo(speed) + ",aformat=sample_rates=48000:channel_layouts=stereo";

        cmd.add("-filter_complex"); cmd.add("[0:v]" + vFilter + "[v];[0:a]" + aFilter + "[a]");
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
        cmd.add("ffmpeg"); cmd.add("-y");
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

        String vFilter = "scale=" + res + ":force_original_aspect_ratio=decrease,pad=" + res + ":(ow-iw)/2:(oh-ih)/2,fps=" + config.getFramerate();
        double speed = clip.getPlaybackSpeed();
        String aFilter = "atempo=" + clampAtempo(speed) + ",aformat=sample_rates=48000:channel_layouts=stereo";

        cmd.add("-filter_complex"); cmd.add("[0:v]" + vFilter + "[v];[1:a]" + aFilter + "[a]");
        cmd.add("-map"); cmd.add("[v]");
        cmd.add("-map"); cmd.add("[a]");
        cmd.add("-t"); cmd.add(String.valueOf(clip.getDurationSeconds()));
        cmd.add("-c:v"); cmd.add(config.getVideoCodec());
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
        cmd.add("ffmpeg"); cmd.add("-y");
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
        cmd.add("ffmpeg"); cmd.add("-y");
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
        cmd.add("ffmpeg"); cmd.add("-y");
        cmd.add("-f"); cmd.add("lavfi");
        cmd.add("-i"); cmd.add("anullsrc=r=48000:cl=stereo");
        cmd.add("-t"); cmd.add(String.valueOf(duration));
        cmd.add("-c:a"); cmd.add("aac");
        cmd.add(out.getAbsolutePath());
        runProcess(cmd, config);
        return out;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private static List<String> buildConcatCmd(File concatList, File masterFile) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg"); cmd.add("-y");
        cmd.add("-f"); cmd.add("concat");
        cmd.add("-safe"); cmd.add("0");
        cmd.add("-i"); cmd.add(concatList.getAbsolutePath());
        cmd.add("-c"); cmd.add("copy");
        cmd.add(masterFile.getAbsolutePath());
        return cmd;
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
            URL url = Exporter.class.getResource("/com/nicch/mediatar/audio_thumbnail.png");
            if (url != null) return new File(url.toURI());
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
