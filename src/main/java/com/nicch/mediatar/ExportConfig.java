package com.nicch.mediatar;

import java.io.File;

public class ExportConfig {
    private String resolution;
    private int framerate;
    private String videoBitrate;
    private String audioBitrate;
    private String videoCodec;
    private String audioCodec;
    private String format;
    private File destinationFile;
    private LogCallback callback;

    public interface LogCallback {
        void onLog(String message);
        void onComplete(boolean success, File outputFile);
    }

    public ExportConfig(String resolution, int framerate, String videoBitrate, String audioBitrate,
                        String videoCodec, String audioCodec, String format,
                        File destinationFile, LogCallback callback) {
        this.resolution = resolution;
        this.framerate = framerate;
        this.videoBitrate = videoBitrate;
        this.audioBitrate = audioBitrate;
        this.videoCodec = videoCodec;
        this.audioCodec = audioCodec;
        this.format = format;
        this.destinationFile = destinationFile;
        this.callback = callback;
    }

    public String getResolution() { return resolution; }
    public int getFramerate() { return framerate; }
    public String getVideoBitrate() { return videoBitrate; }
    public String getAudioBitrate() { return audioBitrate; }
    public String getVideoCodec() { return videoCodec; }
    public String getAudioCodec() { return audioCodec; }
    public String getFormat() { return format; }
    public File getDestinationFile() { return destinationFile; }
    public LogCallback getCallback() { return callback; }

    public boolean isAudioOnlyFormat() {
        String f = format.toLowerCase();
        return f.endsWith(".mp3") || f.endsWith(".aac") || f.endsWith(".m4a") || f.endsWith(".flac") || f.endsWith(".ogg");
    }
}
