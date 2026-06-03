package com.nicch.mediatar;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class MainWorkhorse {

    @FXML private BorderPane rootPane;
    @FXML private ListView<MediaItem> mediaListView;
    @FXML private MediaView mediaView;
    @FXML private javafx.scene.image.ImageView audioThumbnailView;
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
    @FXML private Button btnStepBack, btnRewind, btnFastForward, btnStepForward;
    @FXML private Button btnPlayTimeline, btnStopTimeline;
    @FXML private ToggleButton btnLoop;
    @FXML private ToggleButton btnMagnet;
    @FXML private Slider zoomSlider;
    
    @FXML private Label lblLargeTimecode;
    @FXML private TextField txtClipName;
    @FXML private Label lblClipDuration;
    @FXML private Label lblClipExt;
    @FXML private Slider speedSlider;
    @FXML private Label speedValueLabel;
    @FXML private Slider opacitySlider;
    @FXML private Label opacityValueLabel;
    @FXML private Button btnResetClipProps;
    
    @FXML private Button btnAddText;
    @FXML private Label previewTextOverlay;
    @FXML private VBox textPropertiesBox;
    @FXML private TextField txtTextContent;
    @FXML private ColorPicker textColorPicker;
    @FXML private Slider fontSizeSlider;
    @FXML private Label fontSizeLabel;
    @FXML private VBox videoPropertiesBox;

    private ProgressBar[] vuBars = new ProgressBar[16];

    private ObservableList<MediaItem> mediaItems = FXCollections.observableArrayList();
    private MediaPlayer currentMediaPlayer;
    private boolean isMuted = false;
    private double previousVolume = 0.8;
    private List<TimelineTrack> tracks = new ArrayList<>();
    
    private boolean isPlayingTimeline = false;
    private double globalPlayheadTime = 0.0;
    private TimelineClip activeTimelineClip = null;
    private Map<Integer, TimelineClip> activeAudioClips = new HashMap<>();
    private Map<Integer, MediaPlayer> audioPlayers = new HashMap<>();
    private javafx.animation.AnimationTimer timelineTimer;
    private long lastTimerUpdate = 0;
    public enum TrackType { TEXT, VIDEO, AUDIO }

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
        private double mediaStartOffset = 0.0;
        private double playbackSpeed = 1.0;
        private double opacity = 1.0;
        private String aliasName;
        private boolean isMuted = false;
        
        private String textContent = "New Title";
        private String textColor = "#ffffff";
        private int fontSize = 48;

        public TimelineClip(MediaItem item, double start, double dur, int track) {
            this.mediaItem = item;
            this.startSeconds = start;
            this.durationSeconds = dur;
            this.trackIndex = track;
        }

        public TimelineClip(MediaItem item, double start, double dur, int track, double offset) {
            this.mediaItem = item;
            this.startSeconds = start;
            this.durationSeconds = dur;
            this.trackIndex = track;
            this.mediaStartOffset = offset;
        }

        public TimelineClip(String text, double start, double dur, int track) {
            this.mediaItem = null;
            this.textContent = text;
            this.startSeconds = start;
            this.durationSeconds = dur;
            this.trackIndex = track;
            this.aliasName = "Text Title";
        }

        public MediaItem getMediaItem() { return mediaItem; }
        public double getStartSeconds() { return startSeconds; }
        public void setStartSeconds(double s) { this.startSeconds = s; }
        public double getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(double d) { this.durationSeconds = d; }
        public int getTrackIndex() { return trackIndex; }
        public void setTrackIndex(int t) { this.trackIndex = t; }
        public double getMediaStartOffset() { return mediaStartOffset; }
        public void setMediaStartOffset(double offset) { this.mediaStartOffset = offset; }
        public double getPlaybackSpeed() { return playbackSpeed; }
        public void setPlaybackSpeed(double s) { this.playbackSpeed = s; }
        public double getOpacity() { return opacity; }
        public void setOpacity(double o) { this.opacity = o; }
        public String getAliasName() { return aliasName != null ? aliasName : mediaItem.getFileName(); }
        public void setAliasName(String a) { this.aliasName = a; }
        public boolean isMuted() { return isMuted; }
        public void setMuted(boolean muted) { this.isMuted = muted; }
        
        public String getTextContent() { return textContent; }
        public void setTextContent(String t) { this.textContent = t; this.aliasName = t; }
        public String getTextColor() { return textColor; }
        public void setTextColor(String c) { this.textColor = c; }
        public int getFontSize() { return fontSize; }
        public void setFontSize(int s) { this.fontSize = s; }
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
        setupZoomAndSnapping();
        
        try {
            audioThumbnailView.setImage(new javafx.scene.image.Image(getClass().getResourceAsStream("audio_thumbnail.png")));
        } catch (Exception e) {
            e.printStackTrace();
        }

        tracks.add(new TimelineTrack("T1", TrackType.TEXT));
        tracks.add(new TimelineTrack("V1", TrackType.VIDEO));
        tracks.add(new TimelineTrack("A1", TrackType.AUDIO));
        tracks.add(new TimelineTrack("A2", TrackType.AUDIO));
        tracks.add(new TimelineTrack("A3", TrackType.AUDIO));
        
        timelinePane.setTracks(tracks);
        timelinePane.setPlayheadTime(0.0);
        
        timelinePane.setOnClipSelected(this::updateInspector);
        timelinePane.setOnPlayPauseRequested(this::togglePlayPause);
        
        timelinePane.setOnPlayheadScrubbed(time -> {
            globalPlayheadTime = time;
            evaluateTimelinePlayback(time);
            if (lblLargeTimecode != null) lblLargeTimecode.setText(formatTimecode(time));
        });

        timelineTimer = new javafx.animation.AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastTimerUpdate == 0) {
                    lastTimerUpdate = now;
                    return;
                }
                double deltaSec = (now - lastTimerUpdate) / 1_000_000_000.0;
                lastTimerUpdate = now;
                
                if (isPlayingTimeline) {
                    globalPlayheadTime += deltaSec;
                    if (globalPlayheadTime > timelinePane.getMaxTime()) {
                        stopTimeline();
                        return;
                    }
                    timelinePane.setPlayheadTime(globalPlayheadTime);
                    evaluateTimelinePlayback(globalPlayheadTime);
                    if (lblLargeTimecode != null) lblLargeTimecode.setText(formatTimecode(globalPlayheadTime));
                }
            }
        };
        timelineTimer.start();
        
        txtClipName.textProperty().addListener((obs, oldV, newV) -> {
            TimelineClip clip = timelinePane.getSelectedClip();
            if (clip != null) {
                clip.setAliasName(newV);
                timelinePane.redraw();
            }
        });
        speedSlider.valueProperty().addListener((obs, oldV, newV) -> {
            TimelineClip clip = timelinePane.getSelectedClip();
            if (clip != null) {
                clip.setPlaybackSpeed(newV.doubleValue());
                speedValueLabel.setText(String.format("%.2fx", newV.doubleValue()));
                if (clip == activeTimelineClip && currentMediaPlayer != null) {
                    currentMediaPlayer.setRate(newV.doubleValue());
                }
            }
        });
        opacitySlider.valueProperty().addListener((obs, oldV, newV) -> {
            TimelineClip clip = timelinePane.getSelectedClip();
            if (clip != null) {
                clip.setOpacity(newV.doubleValue());
                opacityValueLabel.setText((int)(newV.doubleValue() * 100) + "%");
                if (clip == activeTimelineClip) {
                    mediaView.setOpacity(newV.doubleValue());
                }
            }
        });
        
        txtTextContent.textProperty().addListener((obs, oldV, newV) -> {
            TimelineClip clip = timelinePane.getSelectedClip();
            if (clip != null && tracks.get(clip.getTrackIndex()).getType() == TrackType.TEXT) {
                clip.setTextContent(newV);
                txtClipName.setText(newV);
                timelinePane.redraw();
            }
        });
        textColorPicker.valueProperty().addListener((obs, oldV, newV) -> {
            TimelineClip clip = timelinePane.getSelectedClip();
            if (clip != null && tracks.get(clip.getTrackIndex()).getType() == TrackType.TEXT) {
                clip.setTextColor("#" + newV.toString().substring(2, 8));
                timelinePane.redraw();
            }
        });
        fontSizeSlider.valueProperty().addListener((obs, oldV, newV) -> {
            int size = newV.intValue();
            fontSizeLabel.setText(String.valueOf(size));
            TimelineClip clip = timelinePane.getSelectedClip();
            if (clip != null && tracks.get(clip.getTrackIndex()).getType() == TrackType.TEXT) {
                clip.setFontSize(size);
            }
        });
    }

    private String formatTimecode(double totalSeconds) {
        int hours = (int) (totalSeconds / 3600);
        int mins = (int) ((totalSeconds % 3600) / 60);
        int secs = (int) (totalSeconds % 60);
        int frames = (int) ((totalSeconds - (int)totalSeconds) * 30);
        return String.format("%02d:%02d:%02d.%02d", hours, mins, secs, frames);
    }

    private void setupTooltips() {
        if (btnImport != null) btnImport.setTooltip(new Tooltip("Import Media:\nLoad video or audio files from your computer into the Media Pool."));
        if (btnSelectTool != null) btnSelectTool.setTooltip(new Tooltip("Selection Tool (V):\nSelect clips, drag to move them, and hover over edges to trim."));
        if (btnBladeTool != null) btnBladeTool.setTooltip(new Tooltip("Blade Tool (C):\nClick anywhere on a clip to split it into two segments."));
        if (btnPlay != null) btnPlay.setTooltip(new Tooltip("Play/Pause Context:\nToggle playback of the currently loaded or selected clip."));
        if (btnExport != null) btnExport.setTooltip(new Tooltip("Export Video:\nRender and save the timeline to a finalized video file."));
        
        if (btnStepBack != null) btnStepBack.setTooltip(new Tooltip("Step Back:\nMove the playhead backward by 1 frame (0.1s)."));
        if (btnStepForward != null) btnStepForward.setTooltip(new Tooltip("Step Forward:\nMove the playhead forward by 1 frame (0.1s)."));
        if (btnRewind != null) btnRewind.setTooltip(new Tooltip("Rewind:\nSkip backward by 5 seconds."));
        if (btnFastForward != null) btnFastForward.setTooltip(new Tooltip("Fast Forward:\nSkip forward by 5 seconds."));
        if (btnLoop != null) btnLoop.setTooltip(new Tooltip("Loop Playback:\nContinuously repeat the current media."));
        if (btnMagnet != null) btnMagnet.setTooltip(new Tooltip("Toggle Snapping:\nEnable/disable magnetic snapping of clips to edges and the playhead."));
        if (zoomSlider != null) zoomSlider.setTooltip(new Tooltip("Timeline Zoom:\nAdjust the scale of the timeline to zoom in or out."));
        if (btnPlayTimeline != null) btnPlayTimeline.setTooltip(new Tooltip("Play Timeline:\nPlay your edited sequence from the current playhead position in the preview window."));
        if (btnStopTimeline != null) btnStopTimeline.setTooltip(new Tooltip("Stop Timeline:\nStop timeline playback and reset the playhead to the beginning."));
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
                        setText("> " + item.getFileName());
                        Tooltip metaTooltip = new Tooltip(String.format("File Metadata:\nName: %s\nPath: %s", 
                            item.getFileName(), item.getFile().getAbsolutePath()));
                        setTooltip(metaTooltip);
                    }
                }
            };
            return cell;
        });

        ContextMenu poolMenu = new ContextMenu();
        MenuItem playPreview = new MenuItem("> Play Preview");
        MenuItem addToV1 = new MenuItem("+ Add to V1 (Video)");
        MenuItem addToA1 = new MenuItem("+ Add to A1 (Audio 1)");
        MenuItem addToA2 = new MenuItem("+ Add to A2 (Audio 2)");
        MenuItem addToA3 = new MenuItem("+ Add to A3 (Audio 3)");
        MenuItem removeFile = new MenuItem("X Remove from Pool");
        
        playPreview.setOnAction(e -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) loadAndPlayMedia(selected);
        });
        
        addToV1.setOnAction(e -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) addToTrackByType(selected, TrackType.VIDEO, 0);
        });
        addToA1.setOnAction(e -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) addToTrackByType(selected, TrackType.AUDIO, 0);
        });
        addToA2.setOnAction(e -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) addToTrackByType(selected, TrackType.AUDIO, 1);
        });
        addToA3.setOnAction(e -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) addToTrackByType(selected, TrackType.AUDIO, 2);
        });
        
        removeFile.setOnAction(e -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) mediaItems.remove(selected);
        });
        
        String fmName = detectFileManager();
        MenuItem showInFM = new MenuItem("Show in " + fmName);

        showInFM.setOnAction(e -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) openInFileManager(selected.getFile());
        });

        poolMenu.getItems().addAll(playPreview, addToV1, addToA1, addToA2, addToA3, new SeparatorMenuItem(), showInFM, new SeparatorMenuItem(), removeFile);
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
                txtClipName.setText(newVal.getFileName());
                txtClipName.setDisable(true);
                speedSlider.setDisable(true);
                opacitySlider.setDisable(true);
                String name = newVal.getFileName();
                int dotIndex = name.lastIndexOf('.');
                lblClipExt.setText("Type: " + (dotIndex > 0 ? name.substring(dotIndex + 1).toUpperCase() : "Unknown"));
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
    
    private void addToTrackByType(MediaItem item, TrackType type, int nth) {
        int count = 0;
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).getType() == type) {
                if (count == nth) {
                    // Place clip after the last existing clip on this track
                    double startAt = tracks.get(i).getClips().stream()
                        .mapToDouble(c -> c.getStartSeconds() + c.getDurationSeconds())
                        .max().orElse(0.0);
                    timelinePane.addClipExternal(item, startAt, i);
                    return;
                }
                count++;
            }
        }
        showAlert("Track Not Found", "No matching track of that type found.");
    }

    private void updateInspector(TimelineClip clip) {
        if (clip == null) {
            txtClipName.setText("--");
            txtClipName.setDisable(true);
            speedSlider.setDisable(true);
            opacitySlider.setDisable(true);
            btnResetClipProps.setDisable(true);
            lblClipDuration.setText("Duration: --");
            lblClipExt.setText("Type: --");
            textPropertiesBox.setVisible(false);
            textPropertiesBox.setManaged(false);
            videoPropertiesBox.setVisible(true);
            videoPropertiesBox.setManaged(true);
        } else {
            txtClipName.setText(clip.getAliasName());
            txtClipName.setDisable(false);
            lblClipDuration.setText(String.format("Duration: %.2fs", clip.getDurationSeconds()));
            
            if (tracks.get(clip.getTrackIndex()).getType() == TrackType.TEXT) {
                lblClipExt.setText("Type: TEXT");
                textPropertiesBox.setVisible(true);
                textPropertiesBox.setManaged(true);
                videoPropertiesBox.setVisible(false);
                videoPropertiesBox.setManaged(false);
                
                txtTextContent.setText(clip.getTextContent());
                textColorPicker.setValue(javafx.scene.paint.Color.web(clip.getTextColor()));
                fontSizeSlider.setValue(clip.getFontSize());
                btnResetClipProps.setDisable(true);
            } else {
                String name = clip.getMediaItem().getFileName();
                int dotIndex = name.lastIndexOf('.');
                lblClipExt.setText("Type: " + (dotIndex > 0 ? name.substring(dotIndex + 1).toUpperCase() : "Unknown"));
                textPropertiesBox.setVisible(false);
                textPropertiesBox.setManaged(false);
                videoPropertiesBox.setVisible(true);
                videoPropertiesBox.setManaged(true);
                
                speedSlider.setDisable(false);
                speedSlider.setValue(clip.getPlaybackSpeed());
                speedValueLabel.setText(String.format("%.2fx", clip.getPlaybackSpeed()));
                opacitySlider.setDisable(false);
                opacitySlider.setValue(clip.getOpacity());
                opacityValueLabel.setText((int)(clip.getOpacity() * 100) + "%");
                btnResetClipProps.setDisable(false);
            }
        }
    }

    @FXML
    private void resetClipProperties() {
        TimelineClip clip = timelinePane.getSelectedClip();
        if (clip == null) return;
        clip.setAliasName(null);
        clip.setPlaybackSpeed(1.0);
        clip.setOpacity(1.0);
        updateInspector(clip);
        if (clip == activeTimelineClip && currentMediaPlayer != null) {
            currentMediaPlayer.setRate(1.0);
            mediaView.setOpacity(1.0);
        }
        timelinePane.redraw();
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
        fc.setTitle("Import Media File(s)");
        
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Media", "*.mp4", "*.mov", "*.mkv", "*.webm", "*.mp3", "*.wav", "*.aac", "*.m4a"),
            new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.mov", "*.mkv", "*.webm"),
            new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.aac", "*.m4a")
        );
        
        List<File> files = fc.showOpenMultipleDialog(rootPane.getScene().getWindow());
        if (files != null) {
            for (File file : files) {
                if (file.getName().toLowerCase().endsWith(".aac")) {
                    File m4aFile = new File(file.getParentFile(), file.getName().substring(0, file.getName().lastIndexOf('.')) + ".m4a");
                    if (!m4aFile.exists()) {
                        try {
                            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-i", file.getAbsolutePath(), "-c:a", "copy", m4aFile.getAbsolutePath());
                            Process p = pb.start();
                            p.waitFor();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (m4aFile.exists()) {
                        mediaItems.add(new MediaItem(m4aFile));
                    } else {
                        mediaItems.add(new MediaItem(file));
                    }
                } else if (file.getName().toLowerCase().endsWith(".webm") || file.getName().toLowerCase().endsWith(".mkv")) {
                    File mp4File = new File(file.getParentFile(), file.getName().substring(0, file.getName().lastIndexOf('.')) + ".mp4");
                    if (!mp4File.exists()) {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Converting " + file.getName() + " to MP4 for JavaFX compatibility. This might take a moment depending on the video length.", javafx.scene.control.ButtonType.OK);
                        alert.show();
                        new Thread(() -> {
                            try {
                                ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-i", file.getAbsolutePath(), "-c:v", "libx264", "-preset", "ultrafast", "-crf", "28", "-c:a", "aac", mp4File.getAbsolutePath());
                                Process p = pb.start();
                                p.waitFor();
                                Platform.runLater(() -> {
                                    alert.close();
                                    if (mp4File.exists()) {
                                        mediaItems.add(new MediaItem(mp4File));
                                    } else {
                                        showAlert("Conversion Failed", "Could not convert " + file.getName());
                                    }
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    } else {
                        mediaItems.add(new MediaItem(mp4File));
                    }
                } else {
                    mediaItems.add(new MediaItem(file));
                }
            }
        }
    }

    @FXML 
    private void togglePlayPause() {
        if (currentMediaPlayer == null && timelinePane.getSelectedClip() == null) {
            if (!tracks.isEmpty() && tracks.stream().anyMatch(t -> !t.getClips().isEmpty())) toggleTimelinePlayback();
            else showAlert("Playback", "Select a clip to play or add clips to the timeline.");
            return;
        }
        if (isPlayingTimeline) {
            toggleTimelinePlayback();
            return;
        }
        if (currentMediaPlayer != null) {
            if (currentMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                currentMediaPlayer.pause();
                btnPlay.setText("Play");
            } else {
                currentMediaPlayer.play();
                btnPlay.setText("Pause");
            }
        } else {
            toggleTimelinePlayback();
        }
    }

    private void toggleTimelinePlayback() {
        isPlayingTimeline = !isPlayingTimeline;
        if (isPlayingTimeline) {
            lastTimerUpdate = 0; 
            btnPlay.setText("Pause");
            if (btnPlayTimeline != null) btnPlayTimeline.setText("Pause Timeline");
            if (currentMediaPlayer != null && currentMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                currentMediaPlayer.stop();
            }
            for (MediaPlayer ap : audioPlayers.values()) {
                if (ap != null && ap.getStatus() == MediaPlayer.Status.PLAYING) ap.stop();
            }
            evaluateTimelinePlayback(globalPlayheadTime);
        } else {
            btnPlay.setText("Play");
            if (btnPlayTimeline != null) btnPlayTimeline.setText("Play Timeline");
            if (currentMediaPlayer != null) currentMediaPlayer.pause();
            for (MediaPlayer ap : audioPlayers.values()) {
                if (ap != null) ap.pause();
            }
        }
    }

    @FXML
    private void playTimeline() {
        if (!tracks.isEmpty() && tracks.stream().anyMatch(t -> !t.getClips().isEmpty())) {
            toggleTimelinePlayback();
        } else {
            showAlert("Timeline", "Add clips to the timeline first by dragging them from the Media Pool.");
        }
    }

    @FXML
    private void handleAddTextAction() {
        if (!tracks.isEmpty() && tracks.get(0).getType() == TrackType.TEXT) {
            TimelineClip textClip = new TimelineClip("New Title", 0, 5.0, 0);
            tracks.get(0).getClips().add(textClip);
            timelinePane.redraw();
        }
    }

    @FXML
    private void stopTimeline() {
        isPlayingTimeline = false;
        globalPlayheadTime = 0.0;
        activeTimelineClip = null;
        activeAudioClips.clear();
        timelinePane.setPlayheadTime(0.0);
        if (currentMediaPlayer != null) {
            currentMediaPlayer.stop();
        }
        for (MediaPlayer ap : audioPlayers.values()) {
            if (ap != null) ap.stop();
        }
        audioPlayers.clear();
        btnPlay.setText("Play");
        if (btnPlayTimeline != null) btnPlayTimeline.setText("Play Timeline");
        noVideoLabel.setVisible(true);
    }

    private void evaluateTimelinePlayback(double globalTime) {
        TimelineClip activeTextClip = null;
        if (!tracks.isEmpty() && tracks.get(0).getType() == TrackType.TEXT) {
            for (TimelineClip clip : tracks.get(0).getClips()) {
                if (globalTime >= clip.getStartSeconds() && globalTime < clip.getStartSeconds() + clip.getDurationSeconds()) {
                    activeTextClip = clip;
                    break;
                }
            }
        }
        if (activeTextClip != null && previewTextOverlay != null) {
            previewTextOverlay.setText(activeTextClip.getTextContent());
            previewTextOverlay.setStyle("-fx-font-size: " + activeTextClip.getFontSize() + "px; -fx-text-fill: " + activeTextClip.getTextColor() + "; -fx-effect: dropshadow(gaussian, black, 2, 1.0, 0, 0);");
            previewTextOverlay.setVisible(true);
        } else if (previewTextOverlay != null) {
            previewTextOverlay.setVisible(false);
        }

        TimelineClip clipUnderPlayhead = null;
        int videoTrackIndex = tracks.size() > 1 && tracks.get(1).getType() == TrackType.VIDEO ? 1 : 0;
        if (!tracks.isEmpty() && tracks.get(videoTrackIndex).getType() == TrackType.VIDEO) {
            for (TimelineClip clip : tracks.get(videoTrackIndex).getClips()) {
                if (globalTime >= clip.getStartSeconds() && globalTime < clip.getStartSeconds() + clip.getDurationSeconds()) {
                    clipUnderPlayhead = clip;
                    break;
                }
            }
        }
        if (clipUnderPlayhead != activeTimelineClip) {
            activeTimelineClip = clipUnderPlayhead;
            if (activeTimelineClip != null) {
                loadAndPlayTimelineClip(activeTimelineClip, globalTime);
                Platform.runLater(() -> updateInspector(activeTimelineClip));
            } else {
                if (currentMediaPlayer != null) {
                    currentMediaPlayer.stop();
                    mediaView.setMediaPlayer(null);
                }
                // No V1 clip – check if any audio track has a clip playing and show thumbnail
                boolean audioIsPlaying = activeAudioClips.values().stream().anyMatch(c -> c != null);
                mediaView.setVisible(false);
                audioThumbnailView.setVisible(audioIsPlaying);
                noVideoLabel.setVisible(!audioIsPlaying);
                if (audioIsPlaying) {
                    // Update inspector with the first active audio clip
                    TimelineClip firstAudio = activeAudioClips.values().stream()
                        .filter(c -> c != null).findFirst().orElse(null);
                    Platform.runLater(() -> updateInspector(firstAudio));
                } else {
                    Platform.runLater(() -> updateInspector(null));
                }
            }
        } else if (activeTimelineClip != null && currentMediaPlayer != null) {
            if (isPlayingTimeline) {
                if (currentMediaPlayer.getStatus() != MediaPlayer.Status.PLAYING) currentMediaPlayer.play();
            } else {
                double localTime = globalTime - activeTimelineClip.getStartSeconds();
                currentMediaPlayer.seek(javafx.util.Duration.seconds(activeTimelineClip.getMediaStartOffset() + localTime));
            }
        }

        for (int trackIdx = 1; trackIdx < tracks.size(); trackIdx++) {
            TimelineClip audioClipUnderPlayhead = null;
            for (TimelineClip clip : tracks.get(trackIdx).getClips()) {
                if (globalTime >= clip.getStartSeconds() && globalTime < clip.getStartSeconds() + clip.getDurationSeconds()) {
                    audioClipUnderPlayhead = clip;
                    break;
                }
            }

            TimelineClip currentlyActive = activeAudioClips.get(trackIdx);
            if (audioClipUnderPlayhead != currentlyActive) {
                activeAudioClips.put(trackIdx, audioClipUnderPlayhead);
                MediaPlayer oldPlayer = audioPlayers.get(trackIdx);
                if (oldPlayer != null) {
                    oldPlayer.stop();
                    oldPlayer.dispose();
                    audioPlayers.remove(trackIdx);
                }
                
                if (audioClipUnderPlayhead != null) {
                    loadAndPlayTimelineAudioClip(trackIdx, audioClipUnderPlayhead, globalTime);
                }
                // If no V1 clip is active, update the preview to reflect audio-only state
                if (activeTimelineClip == null) {
                    boolean anyAudio = activeAudioClips.values().stream().anyMatch(c -> c != null);
                    mediaView.setVisible(false);
                    audioThumbnailView.setVisible(anyAudio);
                    noVideoLabel.setVisible(!anyAudio);
                    TimelineClip fa = activeAudioClips.values().stream().filter(c -> c != null).findFirst().orElse(null);
                    Platform.runLater(() -> updateInspector(fa));
                }
            } else if (currentlyActive != null) {
                MediaPlayer p = audioPlayers.get(trackIdx);
                if (p != null) {
                    if (isPlayingTimeline) {
                        if (p.getStatus() != MediaPlayer.Status.PLAYING) p.play();
                    } else {
                        double localTime = globalTime - currentlyActive.getStartSeconds();
                        p.seek(javafx.util.Duration.seconds(currentlyActive.getMediaStartOffset() + localTime));
                    }
                }
            }
        }
    }
    
    private void loadAndPlayTimelineClip(TimelineClip clip, double globalTime) {
        if (currentMediaPlayer != null) {
            currentMediaPlayer.stop();
            currentMediaPlayer.dispose();
        }
        Media media = new Media(clip.getMediaItem().getFile().toURI().toString());
        currentMediaPlayer = new MediaPlayer(media);
        mediaView.setMediaPlayer(currentMediaPlayer);
        
        // Use track type to determine whether to show video or audio thumbnail
        boolean isAudioTrack = tracks.get(clip.getTrackIndex()).getType() == TrackType.AUDIO;
        if (isAudioTrack) {
            mediaView.setVisible(false);
            audioThumbnailView.setVisible(true);
            noVideoLabel.setVisible(false);
        } else {
            mediaView.setVisible(true);
            audioThumbnailView.setVisible(false);
            noVideoLabel.setVisible(false);
        }

        setupAudioMeters(currentMediaPlayer);
        currentMediaPlayer.setVolume((isMuted || clip.isMuted()) ? 0 : volumeSlider.getValue());
        
        currentMediaPlayer.setOnReady(() -> {
            double localTime = globalTime - clip.getStartSeconds();
            currentMediaPlayer.seek(javafx.util.Duration.seconds(clip.getMediaStartOffset() + localTime));
            currentMediaPlayer.setRate(clip.getPlaybackSpeed());
            mediaView.setOpacity(clip.getOpacity());
            if (isPlayingTimeline) currentMediaPlayer.play();
        });
    }

    private void loadAndPlayTimelineAudioClip(int trackIdx, TimelineClip clip, double globalTime) {
        Media media = new Media(clip.getMediaItem().getFile().toURI().toString());
        MediaPlayer newPlayer = new MediaPlayer(media);
        newPlayer.setVolume((isMuted || clip.isMuted()) ? 0 : volumeSlider.getValue());
        audioPlayers.put(trackIdx, newPlayer);
        
        newPlayer.setOnReady(() -> {
            double localTime = globalTime - clip.getStartSeconds();
            newPlayer.seek(javafx.util.Duration.seconds(clip.getMediaStartOffset() + localTime));
            if (isPlayingTimeline) newPlayer.play();
        });
    }

    private void loadAndPlayMedia(MediaItem item) {
        isPlayingTimeline = false;
        activeTimelineClip = null;
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
                currentMediaPlayer.setCycleCount(btnLoop != null && btnLoop.isSelected() ? MediaPlayer.INDEFINITE : 1);
            });

            mediaView.setMediaPlayer(currentMediaPlayer);
            String fn = item.getFileName().toLowerCase();
            if (fn.endsWith(".mp3") || fn.endsWith(".wav") || fn.endsWith(".aac")) {
                mediaView.setVisible(false);
                audioThumbnailView.setVisible(true);
                noVideoLabel.setVisible(false);
            } else {
                mediaView.setVisible(true);
                audioThumbnailView.setVisible(false);
                noVideoLabel.setVisible(false);
            }
            
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
            btnPlay.setText("Pause");
        } catch (Exception e) {
            showAlert("Playback Error", "Could not play this file. " + e.getMessage());
        }
    }
    
    private void loadMediaWithoutPlaying(MediaItem item) {
        isPlayingTimeline = false;
        activeTimelineClip = null;
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
                currentMediaPlayer.setCycleCount(btnLoop != null && btnLoop.isSelected() ? MediaPlayer.INDEFINITE : 1);
            });

            mediaView.setMediaPlayer(currentMediaPlayer);
            String fn = item.getFileName().toLowerCase();
            if (fn.endsWith(".mp3") || fn.endsWith(".wav") || fn.endsWith(".aac")) {
                mediaView.setVisible(false);
                audioThumbnailView.setVisible(true);
                noVideoLabel.setVisible(false);
            } else {
                mediaView.setVisible(true);
                audioThumbnailView.setVisible(false);
                noVideoLabel.setVisible(false);
            }
            
            setupAudioMeters(currentMediaPlayer);
            
            currentMediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (currentMediaPlayer.getTotalDuration() != null) {
                    if (!playbackSlider.isValueChanging()) {
                        playbackSlider.setValue(newTime.toSeconds() / currentMediaPlayer.getTotalDuration().toSeconds() * 100.0);
                    }
                    playbackTimeLabel.setText(formatTime(newTime) + " / " + formatTime(currentMediaPlayer.getTotalDuration()));
                }
            });
            
            btnPlay.setText("Play");
        } catch (Exception e) {
            System.err.println("Silent load failed: " + e.getMessage());
        }
    }
    
    @FXML 
    private void deleteSelectedClip() {
        timelinePane.deleteSelectedClip();
    }

    private void setupZoomAndSnapping() {
        if (zoomSlider != null) {
            zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                timelinePane.setZoomLevel(newVal.doubleValue());
            });
        }
        if (btnMagnet != null) {
            btnMagnet.selectedProperty().addListener((obs, oldVal, newVal) -> {
                timelinePane.setSnappingEnabled(newVal);
                btnMagnet.setText(newVal ? "Snap" : "Snap");
            });
        }
        if (btnLoop != null) {
            btnLoop.setOnAction(e -> {
                if (currentMediaPlayer != null) {
                    currentMediaPlayer.setCycleCount(btnLoop.isSelected() ? MediaPlayer.INDEFINITE : 1);
                }
            });
        }
    }

    @FXML private void stepBack() {
        if (currentMediaPlayer != null) {
            currentMediaPlayer.seek(currentMediaPlayer.getCurrentTime().subtract(javafx.util.Duration.seconds(0.1)));
        }
    }

    @FXML private void stepForward() {
        if (currentMediaPlayer != null) {
            currentMediaPlayer.seek(currentMediaPlayer.getCurrentTime().add(javafx.util.Duration.seconds(0.1)));
        }
    }

    @FXML private void rewind() {
        if (currentMediaPlayer != null) {
            currentMediaPlayer.seek(currentMediaPlayer.getCurrentTime().subtract(javafx.util.Duration.seconds(5.0)));
        }
    }

    @FXML private void fastForward() {
        if (currentMediaPlayer != null) {
            currentMediaPlayer.seek(currentMediaPlayer.getCurrentTime().add(javafx.util.Duration.seconds(5.0)));
        }
    }

    @FXML private void handleExportAction() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("export_dialog.fxml"));
            Parent root = loader.load();
            ExportDialogController controller = loader.getController();
            controller.setTracks(tracks);

            Stage stage = new Stage();
            stage.setTitle("Advanced Export");
            stage.setScene(new javafx.scene.Scene(root));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.initOwner(rootPane.getScene().getWindow());
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Could not open Export Dialog: " + e.getMessage());
        }
    }
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

    private String detectFileManager() {
        String[] managers = {"nautilus", "nemo", "dolphin", "krusader", "thunar", "pcmanfm", "xdg-open"};
        for (String fm : managers) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"which", fm});
                if (p.waitFor() == 0) {
                    return fm;
                }
            } catch (Exception e) {
            }
        }
        return "xdg-open";
    }

    private void openInFileManager(java.io.File file) {
        if (file == null || !file.exists()) return;
        String fm = detectFileManager();
        try {
            if (fm.equals("nautilus") || fm.equals("nemo") || fm.equals("dolphin")) {
                Runtime.getRuntime().exec(new String[]{fm, "--select", file.getAbsolutePath()});
            } else {
                Runtime.getRuntime().exec(new String[]{fm, file.getParent()});
            }
        } catch (Exception e) {
            showAlert("Error", "Could not open file manager: " + e.getMessage());
        }
    }
}