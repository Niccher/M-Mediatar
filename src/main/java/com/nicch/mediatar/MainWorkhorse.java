package com.nicch.mediatar;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainWorkhorse {

    @FXML private BorderPane rootPane;
    @FXML private ListView<MediaItem> mediaListView;
    @FXML private MediaView mediaView;
    @FXML private Label noVideoLabel;
    @FXML private Slider volumeSlider;
    @FXML private Label volumeValueLabel;
    @FXML private Button btnMute;
    @FXML private javafx.scene.layout.HBox vuMeterBox;
    @FXML private Slider playbackSlider;
    @FXML private Label playbackTimeLabel;
    @FXML private TimelinePane timelinePane;
    
    @FXML private ToggleButton btnSelectTool;
    @FXML private ToggleButton btnBladeTool;
    @FXML private Button btnImport, btnPlay, btnExport;
    
    @FXML private Label lblClipName;
    @FXML private Label lblClipDuration;
    @FXML private Label lblClipExt;
    @FXML private Label lblClipSize;

    private ProgressBar[] vuBars = new ProgressBar[16];

    private ObservableList<MediaItem> mediaItems = FXCollections.observableArrayList();
    private MediaPlayer currentMediaPlayer;
    private boolean isMuted = false;
    private double previousVolume = 0.8;
    private List<TimelineTrack> tracks = new ArrayList<>();
    public enum TrackType { VIDEO, AUDIO }

    public static class MediaItem {
        private final File file;
        public MediaItem(File file) { this.file = file; }
        public File getFile() { return file; }
        public String getFileName() { return file.getName(); }
    }

    public static class TimelineClip {
        private MediaItem mediaItem;
        private double startSeconds;
        private double durationSeconds;
        private int trackIndex;

        public TimelineClip(MediaItem item, double start, double dur, int track) {
            this.mediaItem = item;
            this.startSeconds = start;
            this.durationSeconds = dur;
            this.trackIndex = track;
        }
        public MediaItem getMediaItem() { return mediaItem; }
        public double getStartSeconds() { return startSeconds; }
        public void setStartSeconds(double s) { this.startSeconds = s; }
        public double getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(double d) { this.durationSeconds = d; }
        public int getTrackIndex() { return trackIndex; }
        public void setTrackIndex(int t) { this.trackIndex = t; }
    }

    public static class TimelineTrack {
        private TrackType type;
        private String name;
        private List<TimelineClip> clips = new ArrayList<>();
        
        public TimelineTrack(String name, TrackType type) {
            this.name = name;
            this.type = type;
        }
        public TrackType getType() { return type; }
        public String getName() { return name; }
        public List<TimelineClip> getClips() { return clips; }
    }

    @FXML
    public void initialize() {
        ThemeManager.applyTheme(rootPane);
        
        setupMediaListView();
        initVuMeters();
        setupVolumeControl();
        setupPlaybackSlider();
        setupToolGroup();
        setupTooltips();

        tracks.add(new TimelineTrack("V1", TrackType.VIDEO));
        tracks.add(new TimelineTrack("A1", TrackType.AUDIO));
        tracks.add(new TimelineTrack("A2", TrackType.AUDIO));
        
        timelinePane.setTracks(tracks);
        timelinePane.setPlayheadTime(0.0);
        
        timelinePane.setOnClipSelected(this::updateInspector);
        
        // Pass the play/pause logic down to the timeline's right-click menu
        timelinePane.setOnPlayPauseRequested(this::togglePlayPause);
    }

    private void setupTooltips() {
        btnImport.setTooltip(new Tooltip("Import Media:\nLoad video or audio files from your computer into the Media Pool."));
        btnSelectTool.setTooltip(new Tooltip("Selection Tool (V):\nSelect clips, drag to move them, and hover over edges to trim."));
        btnBladeTool.setTooltip(new Tooltip("Blade Tool (C):\nClick anywhere on a clip to split it into two segments."));
        btnPlay.setTooltip(new Tooltip("Play/Pause Context:\nToggle playback of the currently loaded or selected clip."));
        btnExport.setTooltip(new Tooltip("Export Video:\nRender and save the timeline to a finalized video file."));
    }

    private void setupPlaybackSlider() {
        playbackSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (playbackSlider.isValueChanging() && currentMediaPlayer != null && currentMediaPlayer.getTotalDuration() != null) {
                currentMediaPlayer.seek(currentMediaPlayer.getTotalDuration().multiply(newVal.doubleValue() / 100.0));
            }
        });
        playbackSlider.setOnMouseClicked(e -> {
            if (currentMediaPlayer != null && currentMediaPlayer.getTotalDuration() != null) {
                currentMediaPlayer.seek(currentMediaPlayer.getTotalDuration().multiply(playbackSlider.getValue() / 100.0));
            }
        });
    }

    private void setupToolGroup() {
        ToggleGroup toolGroup = new ToggleGroup();
        btnSelectTool.setToggleGroup(toolGroup);
        btnBladeTool.setToggleGroup(toolGroup);

        toolGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                btnSelectTool.setSelected(true); 
            } else {
                if (btnSelectTool.isSelected()) timelinePane.setToolMode(TimelinePane.ToolMode.SELECTION);
                if (btnBladeTool.isSelected()) timelinePane.setToolMode(TimelinePane.ToolMode.BLADE);
            }
        });
        
        rootPane.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.V) btnSelectTool.fire();
            if (e.getCode() == KeyCode.C) btnBladeTool.fire();
            if (e.getCode() == KeyCode.SPACE) togglePlayPause();
            if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) deleteSelectedClip();
        });
    }

    private void setupMediaListView() {
        mediaListView.setItems(mediaItems);
        mediaListView.setCellFactory(lv -> {
            ListCell<MediaItem> cell = new ListCell<>() {
                @Override protected void updateItem(MediaItem item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setTooltip(null);
                    } else {
                        setText("🎞 " + item.getFileName());
                        Tooltip metaTooltip = new Tooltip(String.format("File Metadata:\nName: %s\nPath: %s", 
                            item.getFileName(), item.getFile().getAbsolutePath()));
                        setTooltip(metaTooltip);
                    }
                }
            };
            return cell;
        });

        ContextMenu poolMenu = new ContextMenu();
        MenuItem playPreview = new MenuItem("▶ Play Preview");
        MenuItem addToTimeline = new MenuItem("➕ Add to Timeline (V1)");
        MenuItem removeFile = new MenuItem("🗑 Remove from Pool");
        
        playPreview.setOnAction(e -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) loadAndPlayMedia(selected);
        });
        
        addToTimeline.setOnAction(e -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) timelinePane.addClipExternal(selected, 0.0, 0); 
        });

        removeFile.setOnAction(e -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) mediaItems.remove(selected);
        });
        
        poolMenu.getItems().addAll(playPreview, addToTimeline, new SeparatorMenuItem(), removeFile);
        mediaListView.setContextMenu(poolMenu);

        mediaListView.setOnDragDetected(event -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Dragboard db = mediaListView.startDragAndDrop(TransferMode.COPY);
                ClipboardContent content = new ClipboardContent();
                content.putString(selected.getFile().getAbsolutePath());
                db.setContent(content);
                event.consume();
            }
        });

        mediaListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                lblClipName.setText("Name: " + newVal.getFileName());
                String name = newVal.getFileName();
                int dotIndex = name.lastIndexOf('.');
                lblClipExt.setText("Type: " + (dotIndex > 0 ? name.substring(dotIndex + 1).toUpperCase() : "Unknown"));
                long bytes = newVal.getFile().length();
                if (bytes > 1024 * 1024) {
                    lblClipSize.setText(String.format("Size: %.2f MB", bytes / (1024.0 * 1024.0)));
                } else {
                    lblClipSize.setText(String.format("Size: %.2f KB", bytes / 1024.0));
                }
                lblClipDuration.setText("Duration: Loading...");
                loadMediaWithoutPlaying(newVal);
            }
        });

        mediaListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    loadAndPlayMedia(selected);
                }
            }
        });
    }
    
    private void updateInspector(TimelineClip clip) {
        if (clip == null) {
            lblClipName.setText("Name: --");
            lblClipDuration.setText("Duration: --");
        } else {
            lblClipName.setText("Name: " + clip.getMediaItem().getFileName());
            lblClipDuration.setText(String.format("Duration: %.2fs", clip.getDurationSeconds()));
            // Automatically load it into the player, but don't play it yet
            loadMediaWithoutPlaying(clip.getMediaItem());
        }
    }

    private String formatTime(javafx.util.Duration duration) {
        if (duration == null || duration.isUnknown()) return "00:00";
        int totalSeconds = (int) Math.floor(duration.toSeconds());
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private void setupVolumeControl() {
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (currentMediaPlayer != null && !isMuted) currentMediaPlayer.setVolume(newVal.doubleValue());
            volumeValueLabel.setText((int) (newVal.doubleValue() * 100) + "%");
        });
    }

    private void initVuMeters() {
        vuMeterBox.getChildren().clear();
        for (int i = 0; i < 16; i++) {
            ProgressBar bar = new ProgressBar(0.0);
            bar.setPrefWidth(60);
            bar.setPrefHeight(10);
            bar.setRotate(-90);
            bar.setStyle("-fx-accent: #859900;");
            javafx.scene.Group group = new javafx.scene.Group(bar);
            vuMeterBox.getChildren().add(group);
            vuBars[i] = bar;
        }
    }

    private void setupAudioMeters(MediaPlayer player) {
        player.setAudioSpectrumNumBands(16);
        player.setAudioSpectrumInterval(0.05); // 50ms updates
        player.setAudioSpectrumListener((double timestamp, double duration, float[] magnitudes, float[] phases) -> {
            if (magnitudes != null) {
                for (int i = 0; i < magnitudes.length && i < 16; i++) {
                    double prog = Math.max(0, (magnitudes[i] + 60) / 60.0);
                    vuBars[i].setProgress(prog);
                    String style = prog > 0.85 ? "-fx-accent: #dc322f;" : (prog > 0.65 ? "-fx-accent: #b58900;" : "-fx-accent: #859900;");
                    vuBars[i].setStyle(style);
                }
            }
        });
    }

    @FXML 
    private void toggleMute() {
        if (currentMediaPlayer == null) {
            isMuted = !isMuted;
            btnMute.setText(isMuted ? "Unmute" : "Mute Track");
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
        btnMute.setText(isMuted ? "Unmute" : "Mute Track");
    }

    @FXML 
    private void handleImportAction() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import Media File");
        
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Media", "*.mp4", "*.mov", "*.mkv", "*.webm", "*.mp3", "*.wav", "*.aac"),
            new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.mov", "*.mkv", "*.webm"),
            new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.aac")
        );
        
        File file = fc.showOpenDialog(rootPane.getScene().getWindow());
        if (file != null) mediaItems.add(new MediaItem(file));
    }

    @FXML 
    private void togglePlayPause() {
        // If nothing is loaded in the player, try to load the selected timeline clip
        if (currentMediaPlayer == null) {
            TimelineClip clip = timelinePane.getSelectedClip();
            if (clip != null) {
                loadAndPlayMedia(clip.getMediaItem());
            } else {
                showAlert("Playback", "Select a clip to play.");
            }
            return;
        }

        // True Play/Pause toggle
        if (currentMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            currentMediaPlayer.pause();
            btnPlay.setText("▶ Play");
        } else {
            currentMediaPlayer.play();
            btnPlay.setText("⏸ Pause");
        }
    }

    private void loadAndPlayMedia(MediaItem item) {
        try {
            if (currentMediaPlayer != null) {
                currentMediaPlayer.stop();
                currentMediaPlayer.dispose();
            }
            Media media = new Media(item.getFile().toURI().toString());
            currentMediaPlayer = new MediaPlayer(media);
            
            currentMediaPlayer.setOnReady(() -> {
                if (currentMediaPlayer.getTotalDuration() != null) {
                    lblClipDuration.setText(String.format("Duration: %s", formatTime(currentMediaPlayer.getTotalDuration())));
                }
            });

            mediaView.setMediaPlayer(currentMediaPlayer);
            noVideoLabel.setVisible(false);
            
            setupAudioMeters(currentMediaPlayer);
            
            currentMediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (currentMediaPlayer.getTotalDuration() != null) {
                    if (!playbackSlider.isValueChanging()) {
                        playbackSlider.setValue(newTime.toSeconds() / currentMediaPlayer.getTotalDuration().toSeconds() * 100.0);
                    }
                    playbackTimeLabel.setText(formatTime(newTime) + " / " + formatTime(currentMediaPlayer.getTotalDuration()));
                }
            });
            
            currentMediaPlayer.setVolume(isMuted ? 0 : volumeSlider.getValue());
            currentMediaPlayer.play();
            btnPlay.setText("⏸ Pause");
        } catch (Exception e) {
            showAlert("Playback Error", "Could not play this file. " + e.getMessage());
        }
    }
    
    private void loadMediaWithoutPlaying(MediaItem item) {
        try {
            if (currentMediaPlayer != null) {
                currentMediaPlayer.stop();
                currentMediaPlayer.dispose();
            }
            Media media = new Media(item.getFile().toURI().toString());
            currentMediaPlayer = new MediaPlayer(media);
            
            currentMediaPlayer.setOnReady(() -> {
                if (currentMediaPlayer.getTotalDuration() != null) {
                    lblClipDuration.setText(String.format("Duration: %s", formatTime(currentMediaPlayer.getTotalDuration())));
                }
            });

            mediaView.setMediaPlayer(currentMediaPlayer);
            noVideoLabel.setVisible(false);
            
            setupAudioMeters(currentMediaPlayer);
            
            currentMediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (currentMediaPlayer.getTotalDuration() != null) {
                    if (!playbackSlider.isValueChanging()) {
                        playbackSlider.setValue(newTime.toSeconds() / currentMediaPlayer.getTotalDuration().toSeconds() * 100.0);
                    }
                    playbackTimeLabel.setText(formatTime(newTime) + " / " + formatTime(currentMediaPlayer.getTotalDuration()));
                }
            });
            
            btnPlay.setText("▶ Play");
        } catch (Exception e) {
            System.err.println("Silent load failed: " + e.getMessage());
        }
    }
    
    @FXML 
    private void deleteSelectedClip() {
        timelinePane.deleteSelectedClip();
    }

    @FXML private void handleExportAction() { showAlert("Export", "Export functionality is not yet implemented."); }
    @FXML private void handleSaveAction() { showAlert("Save", "Save functionality is not yet implemented."); }
    @FXML private void handleExit() { Platform.exit(); System.exit(0); }
    
    @FXML private void applySolarizedTheme() { ThemeManager.currentTheme = ThemeManager.Theme.SOLARIZED; ThemeManager.applyTheme(rootPane); }
    @FXML private void applyDarkTheme() { ThemeManager.currentTheme = ThemeManager.Theme.DARK; ThemeManager.applyTheme(rootPane); }
    @FXML private void applyLightTheme() { ThemeManager.currentTheme = ThemeManager.Theme.LIGHT; ThemeManager.applyTheme(rootPane); }
    
    private void showAlert(String title, String msg) { 
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}