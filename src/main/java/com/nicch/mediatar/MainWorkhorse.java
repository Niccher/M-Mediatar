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
    @FXML private TimelinePane timelinePane;
    
    @FXML private ToggleButton btnSelectTool;
    @FXML private ToggleButton btnBladeTool;
    @FXML private Button btnImport, btnPlay, btnExport;
    
    @FXML private Label lblClipName;
    @FXML private Label lblClipDuration;

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
        setupVolumeControl();
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

    private void setupVolumeControl() {
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (currentMediaPlayer != null && !isMuted) currentMediaPlayer.setVolume(newVal.doubleValue());
            volumeValueLabel.setText((int) (newVal.doubleValue() * 100) + "%");
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
            mediaView.setMediaPlayer(currentMediaPlayer);
            noVideoLabel.setVisible(false);
            
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
            mediaView.setMediaPlayer(currentMediaPlayer);
            noVideoLabel.setVisible(false);
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