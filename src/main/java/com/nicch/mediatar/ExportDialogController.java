package com.nicch.mediatar;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import java.io.File;
import java.util.List;

public class ExportDialogController {

    @FXML private BorderPane rootPane;
    @FXML private ComboBox<String> cbResolution;
    @FXML private ComboBox<Integer> cbFramerate;
    @FXML private ComboBox<String> cbBitrate;
    @FXML private ComboBox<String> cbVideoCodec;
    @FXML private ComboBox<String> cbAudioCodec;
    @FXML private ComboBox<String> cbAudioBitrate;
    @FXML private ComboBox<String> cbFormat;
    @FXML private TextField txtFilename;
    @FXML private TextField txtDestDir;
    @FXML private TextArea terminalOutput;
    @FXML private Label lblRenderTime;
    @FXML private Button btnRender;
    @FXML private Button btnClose;
    @FXML private Button btnOpenFolder;

    private List<MainWorkhorse.TimelineTrack> tracks;
    private File selectedDirectory;
    private File lastRenderedFile;
    private String detectedFileManager;

    @FXML
    public void initialize() {
        cbResolution.setItems(FXCollections.observableArrayList(
            "7680x4320 (8K)", "3840x2160 (4K UHD)", "2560x1440 (2K QHD)",
            "1920x1080 (Full HD)", "1280x720 (HD Ready)", "854x480 (SD)",
            "640x360 (360p)", "426x240 (240p)"
        ));
        cbResolution.setValue("1920x1080 (Full HD)");

        cbFramerate.setItems(FXCollections.observableArrayList(24, 25, 30, 50, 60, 120));
        cbFramerate.setValue(30);

        cbBitrate.setItems(FXCollections.observableArrayList(
            "500k", "1000k", "2000k", "3000k", "5000k", "8000k", "10000k", "15000k", "20000k", "50000k"
        ));
        cbBitrate.setValue("5000k");

        cbVideoCodec.setItems(FXCollections.observableArrayList("libx264", "libx265", "vp9", "mpeg4", "copy"));
        cbVideoCodec.setValue("libx264");

        cbAudioCodec.setItems(FXCollections.observableArrayList("aac", "mp3", "flac", "libvorbis", "opus", "pcm_s16le", "copy"));
        cbAudioCodec.setValue("aac");

        cbAudioBitrate.setItems(FXCollections.observableArrayList("64k", "96k", "128k", "192k", "256k", "320k"));
        cbAudioBitrate.setValue("192k");

        cbFormat.setItems(FXCollections.observableArrayList(
            ".mp4", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".mp3", ".aac", ".m4a", ".flac", ".ogg"
        ));
        cbFormat.setValue(".mp4");

        detectedFileManager = detectFileManager();

        // When format changes to audio-only, disable video settings and auto-select correct audio codec
        cbFormat.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean audioOnly = isAudioOnlyExt(newVal);
            cbResolution.setDisable(audioOnly);
            cbFramerate.setDisable(audioOnly);
            cbBitrate.setDisable(audioOnly);
            cbVideoCodec.setDisable(audioOnly);

            // Force compatible audio codec for strict containers
            if (".mp3".equals(newVal)) {
                cbAudioCodec.setValue("mp3");
            } else if (".flac".equals(newVal)) {
                cbAudioCodec.setValue("flac");
            } else if (".ogg".equals(newVal)) {
                cbAudioCodec.setValue("libvorbis");
            } else if (".m4a".equals(newVal) || ".aac".equals(newVal)) {
                cbAudioCodec.setValue("aac");
            }
        });

        // Apply the current global theme to this dialog
        ThemeManager.applyTheme(rootPane);
    }

    public void setTracks(List<MainWorkhorse.TimelineTrack> tracks) {
        this.tracks = tracks;
        
        // Auto-select audio format if the timeline only contains audio clips
        if (tracks != null && !tracks.isEmpty()) {
            boolean hasVideo = tracks.stream()
                .filter(t -> t.getType() == MainWorkhorse.TrackType.VIDEO)
                .anyMatch(t -> !t.getClips().isEmpty());
            boolean hasAudio = tracks.stream()
                .filter(t -> t.getType() == MainWorkhorse.TrackType.AUDIO)
                .anyMatch(t -> !t.getClips().isEmpty());
            
            if (!hasVideo && hasAudio) {
                cbFormat.setValue(".mp3");
            }
        }
    }

    @FXML
    private void browseDirectory() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Destination Directory");
        File dir = dc.showDialog(txtDestDir.getScene().getWindow());
        if (dir != null) {
            selectedDirectory = dir;
            txtDestDir.setText(dir.getAbsolutePath());
        }
    }

    @FXML
    private void closeDialog() {
        ((Stage) btnClose.getScene().getWindow()).close();
    }

    @FXML
    private void openOutputFolder() {
        if (lastRenderedFile != null && lastRenderedFile.getParentFile().exists()) {
            openInFileManager(lastRenderedFile);
        }
    }

    @FXML
    private void startRender() {
        if (selectedDirectory == null) {
            showAlert("Error", "Please select a destination directory first.");
            return;
        }

        String filename = txtFilename.getText().trim();
        if (filename.isEmpty()) filename = "Final_Render";

        String videoBitrate = cbBitrate.getValue() == null ? "5000k" : cbBitrate.getValue().trim();
        if (videoBitrate.matches("\\d+")) videoBitrate += "k";

        String audioBitrate = cbAudioBitrate.getValue() == null ? "192k" : cbAudioBitrate.getValue().trim();
        if (audioBitrate.matches("\\d+")) audioBitrate += "k";

        String ext = cbFormat.getValue();
        if (filename.toLowerCase().endsWith(ext)) {
            filename = filename.substring(0, filename.length() - ext.length());
        }

        File destFile = new File(selectedDirectory, filename + ext);
        if (destFile.exists()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "File already exists. Overwrite?", ButtonType.YES, ButtonType.NO);
            alert.showAndWait();
            if (alert.getResult() != ButtonType.YES) return;
        }

        // Parse resolution string to actual pixels
        String resFull = cbResolution.getValue();
        String resolution = resFull.contains(" ") ? resFull.split(" ")[0] : resFull;

        btnRender.setDisable(true);
        btnClose.setDisable(true);
        btnOpenFolder.setVisible(false);
        terminalOutput.clear();
        lblRenderTime.setText("Rendering...");

        appendLog("══════════════ MEDIATAR RENDER ENGINE ══════════════");
        appendLog("Destination  : " + destFile.getAbsolutePath());
        appendLog("Resolution   : " + resolution + " @ " + cbFramerate.getValue() + "fps");
        appendLog("Video Codec  : " + cbVideoCodec.getValue() + " | Bitrate: " + videoBitrate);
        appendLog("Audio Codec  : " + cbAudioCodec.getValue() + " | Bitrate: " + audioBitrate);
        appendLog("Container    : " + ext);
        appendLog("════════════════════════════════════════════════════\n");

        lastRenderedFile = destFile;
        long startTime = System.currentTimeMillis();

        ExportConfig config = new ExportConfig(
            resolution,
            cbFramerate.getValue(),
            videoBitrate,
            audioBitrate,
            cbVideoCodec.getValue(),
            cbAudioCodec.getValue(),
            ext,
            destFile,
            new ExportConfig.LogCallback() {
                @Override public void onLog(String message) { appendLog(message); }
                @Override public void onComplete(boolean success, File outputFile) {
                    Platform.runLater(() -> {
                        btnRender.setDisable(false);
                        btnClose.setDisable(false);
                        if (success) {
                            long durationSec = (System.currentTimeMillis() - startTime) / 1000;
                            lblRenderTime.setText(String.format("⏱ Completed in %ds", durationSec));
                            appendLog("\n✅ EXPORT COMPLETED SUCCESSFULLY.");
                            btnOpenFolder.setVisible(true);
                            Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                                "Render completed!\n" + outputFile.getAbsolutePath() + "\n\nOpen output folder now?",
                                ButtonType.YES, ButtonType.NO);
                            a.setTitle("Export Complete");
                            a.showAndWait();
                            if (a.getResult() == ButtonType.YES) openInFileManager(outputFile);
                        } else {
                            lblRenderTime.setText("❌ Failed");
                            lblRenderTime.setStyle("-fx-font-weight: bold; -fx-text-fill: #e74c3c;");
                            appendLog("\n❌ EXPORT FAILED. Check the log above for errors.");
                            showAlert("Export Failed", "Render failed. Check the terminal output.");
                        }
                    });
                }
            }
        );

        Exporter.exportTimeline(tracks, config);
    }

    private void appendLog(String msg) {
        Platform.runLater(() -> terminalOutput.appendText(msg + "\n"));
    }

    private boolean isAudioOnlyExt(String ext) {
        if (ext == null) return false;
        String e = ext.toLowerCase();
        return e.endsWith(".mp3") || e.endsWith(".aac") || e.endsWith(".m4a") || e.endsWith(".flac") || e.endsWith(".ogg");
    }

    private String detectFileManager() {
        String[] managers = {"nautilus", "nemo", "dolphin", "krusader", "thunar", "pcmanfm", "xdg-open"};
        for (String fm : managers) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"which", fm});
                if (p.waitFor() == 0) return fm;
            } catch (Exception ignored) {}
        }
        return "xdg-open";
    }

    private void openInFileManager(File file) {
        if (file == null) return;
        String fm = detectFileManager();
        try {
            if (fm.equals("nautilus") || fm.equals("nemo") || fm.equals("dolphin")) {
                Runtime.getRuntime().exec(new String[]{fm, "--select", file.getAbsolutePath()});
            } else {
                Runtime.getRuntime().exec(new String[]{fm, file.getParentFile().getAbsolutePath()});
            }
        } catch (Exception e) {
            showAlert("Error", "Could not open file manager: " + e.getMessage());
        }
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
