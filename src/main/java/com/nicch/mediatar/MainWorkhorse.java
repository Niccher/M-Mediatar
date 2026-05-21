package com.nicch.mediatar;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import java.io.File;
import java.time.LocalTime;

public class MainWorkhorse {

    @FXML private BorderPane rootPane;
    @FXML private StackPane mainStackPane;
    @FXML private VBox welcomeContainer;
    @FXML private SplitPane mainSplitPane;

    @FXML private ListView<MediaItem> mediaListView;
    @FXML private TextField searchField;

    @FXML private Button cardOpenFile;
    @FXML private Button cardOpenProject;

    @FXML private MediaView mediaView;
    @FXML private Label noVideoLabel;
    @FXML private Slider volumeSlider;
    @FXML private Label volumeValueLabel;
    @FXML private Button btnMute;

    private SVGPath iconFile;
    private SVGPath iconProject;

    private ObservableList<MediaItem> mediaItems = FXCollections.observableArrayList();
    private MediaPlayer currentMediaPlayer;

    private double previousVolume = 0.8;   // Store volume before mute
    private boolean isMuted = false;

    @FXML
    public void initialize() {
        setupCardIcons();
        setupMediaListView();
        setupContextMenu();
        setupVolumeControl();
        setupMuteButton();

        String faintCardStyle = "-fx-background-color: transparent; -fx-border-color: rgba(128,128,128,0.35); " +
                               "-fx-border-width: 1.5; -fx-border-radius: 12; -fx-cursor: hand;";
        cardOpenFile.setStyle(faintCardStyle);
        cardOpenProject.setStyle(faintCardStyle);

        applySolarizedTheme();
        logMessage("MMediatar Workspace initialized.");
    }

    private void setupCardIcons() {
        iconFile = new SVGPath();
        iconFile.setContent("M18 4v1h-2V4c0-.55-.45-1-1-1H9c-.55 0-1 .45-1 1v1H6V4c0-.55-.45-1-1-1s-1 .45-1 1v16c0 .55.45 1 1 1s1-.45 1-1v-1h2v1c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-1h2v1c0 .55.45 1 1 1s1-.45 1-1V4c0-.55-.45-1-1-1s-1 .45-1 1zM8 17H6v-2h2v2zm0-4H6v-2h2v2zm0-4H6V7h2v2zm10 8h-2v-2h2v2zm0-4h-2v-2h2v2zm0-4h-2V7h2v2z");
        iconFile.setScaleX(2.6);
        iconFile.setScaleY(2.6);

        iconProject = new SVGPath();
        iconProject.setContent("M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z");
        iconProject.setScaleX(2.6);
        iconProject.setScaleY(2.6);

        cardOpenFile.setGraphic(iconFile);
        cardOpenProject.setGraphic(iconProject);
    }

    private void setupMediaListView() {
        mediaListView.setItems(mediaItems);
        mediaListView.setCellFactory(lv -> new ListCell<MediaItem>() {
            @Override
            protected void updateItem(MediaItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item.getDisplayName());
                    setTooltip(new Tooltip(item.getTooltipInfo()));
                }
            }
        });

        // Double-click to play
        mediaListView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                playSelectedMedia();
            }
        });
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem removeItem = new MenuItem("Remove from List");
        removeItem.setOnAction(e -> removeSelectedMedia());
        contextMenu.getItems().add(removeItem);
        mediaListView.setContextMenu(contextMenu);
    }

    private void setupVolumeControl() {
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (currentMediaPlayer != null && !isMuted) {
                currentMediaPlayer.setVolume(newVal.doubleValue());
            }
            int percent = (int) (newVal.doubleValue() * 100);
            volumeValueLabel.setText(percent + "%");
        });
    }

    private void setupMuteButton() {
        btnMute.setOnAction(e -> toggleMute());
    }

    @FXML
    private void toggleMute() {
        if (currentMediaPlayer == null) {
            isMuted = !isMuted;
            updateMuteButtonText();
            return;
        }

        isMuted = !isMuted;

        if (isMuted) {
            previousVolume = currentMediaPlayer.getVolume();
            currentMediaPlayer.setVolume(0.0);
            volumeSlider.setDisable(true);
        } else {
            currentMediaPlayer.setVolume(previousVolume);
            volumeSlider.setValue(previousVolume);
            volumeSlider.setDisable(false);
        }

        updateMuteButtonText();
        logMessage(isMuted ? "🔇 Muted" : "🔊 Unmuted");
    }

    private void updateMuteButtonText() {
        btnMute.setText(isMuted ? "🔊 Unmute" : "🔇 Mute");
    }

    private void removeSelectedMedia() {
        MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            mediaItems.remove(selected);
            logMessage("Removed: " + selected.getFileName());
        }
    }

    @FXML
    private void handleImportAction() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open Media File");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Media", "*.mp4", "*.mkv", "*.mov", "*.avi", "*.mp3", "*.wav", "*.aac"),
            new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.mkv", "*.mov", "*.avi"),
            new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.aac")
        );

        File file = fc.showOpenDialog(rootPane.getScene().getWindow());
        if (file != null) {
            MediaItem item = new MediaItem(file);
            mediaItems.add(item);

            cardOpenFile.setText("Loaded:\n" + file.getName());
            cardOpenFile.setStyle("-fx-background-color: rgba(46, 204, 113, 0.15); -fx-border-color: #2ecc71; -fx-border-width: 2; -fx-border-radius: 12;");

            if (!mainSplitPane.isVisible()) {
                switchToEditor();
            }
            logMessage("Imported: " + file.getName());
        }
    }

    private void switchToEditor() {
        welcomeContainer.setVisible(false);
        mainSplitPane.setVisible(true);
    }

    @FXML
    private void playSelectedMedia() {
        MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No Selection", "Please select a file from the list.");
            return;
        }

        try {
            stopCurrentPlayback();

            Media media = new Media(selected.getFile().toURI().toString());
            currentMediaPlayer = new MediaPlayer(media);
            
            mediaView.setMediaPlayer(currentMediaPlayer);
            noVideoLabel.setVisible(false);

            double volumeToUse = isMuted ? 0.0 : volumeSlider.getValue();
            currentMediaPlayer.setVolume(volumeToUse);
            currentMediaPlayer.play();

            logMessage("▶ Playing: " + selected.getFileName());
        } catch (Exception e) {
            showAlert("Playback Error", "Cannot play this file.");
            e.printStackTrace();
        }
    }

    @FXML
    private void pauseSelectedMedia() {
        if (currentMediaPlayer != null) {
            currentMediaPlayer.pause();
            logMessage("⏸ Paused");
        }
    }

    private void stopCurrentPlayback() {
        if (currentMediaPlayer != null) {
            currentMediaPlayer.stop();
            currentMediaPlayer.dispose();
            currentMediaPlayer = null;
        }
        noVideoLabel.setVisible(true);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // ====================== THEMES & OTHER HANDLERS ======================
    @FXML public void applySolarizedTheme() {
        rootPane.setStyle("-fx-base: #eee8d5; -fx-background: #fdf6e3; -fx-text-base-color: #586e75;");
        logMessage("Theme: SOLARIZED");
    }

    @FXML public void applyDarkTheme() {
        rootPane.setStyle("-fx-base: #1e222b; -fx-background: #2f3542; -fx-text-base-color: #ffffff;");
        logMessage("Theme: DARK");
    }

    @FXML public void applyCyberpunkTheme() {
        rootPane.setStyle("-fx-base: #1a1c29; -fx-background: #0d0e15; -fx-text-base-color: #00ffcc;");
        logMessage("Theme: CYBERPUNK");
    }

    @FXML public void applyDraculaTheme() {
        rootPane.setStyle("-fx-base: #1e1f29; -fx-background: #282a36; -fx-text-base-color: #f8f8f2;");
        logMessage("Theme: DRACULA");
    }

    @FXML private void handleOpenProjectAction() { logMessage("Open Project clicked."); }
    @FXML private void handleSaveAction() { logMessage("Save triggered."); }
    @FXML private void handleSaveAsAction() { logMessage("Save As triggered."); }
    @FXML private void handleSearchAction() { logMessage("Search triggered."); }
    @FXML private void handleRunPipeline() { logMessage("Pipeline started."); }
    @FXML private void handleRefreshAction() { logMessage("Cache flushed."); }
    @FXML private void handleOpenOptions() { logMessage("Preferences opened."); }
    @FXML private void handleExit() { Platform.exit(); System.exit(0); }

    @FXML private void handleAboutAction() {
        new Alert(Alert.AlertType.INFORMATION, "MMediatar Studio Pro v2.4.0").showAndWait();
    }

    private void logMessage(String text) {
        System.out.println("[" + LocalTime.now().toString().substring(0, 8) + "] " + text);
    }

    // ====================== MEDIA ITEM ======================
    public static class MediaItem {
        private final File file;

        public MediaItem(File file) {
            this.file = file;
        }

        public File getFile() { return file; }
        public String getFileName() { return file.getName(); }
        public String getDisplayName() { return file.getName(); }

        public String getTooltipInfo() {
            String ext = getExtension();
            long sizeKB = file.length() / 1024;
            return "File: " + file.getName() + "\nSize: " + sizeKB + " KB\nType: " + ext.toUpperCase();
        }

        private String getExtension() {
            String name = file.getName();
            int last = name.lastIndexOf('.');
            return last > 0 ? name.substring(last + 1) : "unknown";
        }
    }
}