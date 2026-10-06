package com.nicch.mediatar;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
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
    @FXML private StackPane previewContainer;
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
    @FXML private Button btnImport, btnPlay, btnExport, btnRenderStatus;
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
    @FXML private Slider textOpacitySlider;
    @FXML private Label textOpacityValueLabel;
    @FXML private VBox videoPropertiesBox;
    @FXML private MediaView mediaViewV2;
    @FXML private Slider scaleSlider;
    @FXML private Label scaleValueLabel;
    @FXML private Slider posXSlider;
    @FXML private Label posXValueLabel;
    @FXML private Slider posYSlider;
    @FXML private Label posYValueLabel;

    private ProgressBar[] vuBars = new ProgressBar[16];

    private ObservableList<MediaItem> mediaItems = FXCollections.observableArrayList();
    private MediaPlayer currentMediaPlayer;
    private boolean isMuted = false;
    private double previousVolume = 0.8;
    private List<TimelineTrack> tracks = new ArrayList<>();
    
    private boolean isPlayingTimeline = false;
    private double globalPlayheadTime = 0.0;
    private Stage exportStage;
    private ExportDialogController exportController;
    private TimelineClip activeTimelineClip = null;
    private TimelineClip activeTimelineClipV2 = null;
    private MediaPlayer currentPlayerV2;
    private Map<Integer, TimelineClip> activeAudioClips = new HashMap<>();
    private Map<Integer, MediaPlayer> audioPlayers = new HashMap<>();
    private javafx.animation.AnimationTimer timelineTimer;
    private long lastTimerUpdate = 0;
    public enum TrackType { TEXT, VIDEO, AUDIO }

    public static class MediaItem {
        private final File file;
        private float[] audioPeaks;
        private final List<Runnable> onLoadCallbacks = new ArrayList<>();
        private boolean isLoaded = false;

        public MediaItem(File file) {
            this.file = file;
            extractPeaksAsync();
        }

        public File getFile() { return file; }
        public String getFileName() { return file.getName(); }
        
        public synchronized float[] getAudioPeaks() {
            return audioPeaks;
        }

        public synchronized void addOnLoadCallback(Runnable callback) {
            if (isLoaded) {
                Platform.runLater(callback);
            } else {
                onLoadCallbacks.add(callback);
            }
        }

        private void extractPeaksAsync() {
            new Thread(() -> {
                float[] peaks = extractPeaks(file);
                synchronized (MediaItem.this) {
                    audioPeaks = peaks;
                    isLoaded = true;
                    for (Runnable callback : onLoadCallbacks) {
                        Platform.runLater(callback);
                    }
                    onLoadCallbacks.clear();
                }
            }).start();
        }

        private float[] extractPeaks(File mediaFile) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", mediaFile.getAbsolutePath(),
                    "-ac", "1", "-ar", "50", "-f", "s8", "-"
                );
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                
                Process p = pb.start();
                java.io.InputStream is = p.getInputStream();
                
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                p.waitFor();
                
                byte[] rawBytes = baos.toByteArray();
                if (rawBytes.length == 0) return null;
                
                float[] peaks = new float[rawBytes.length];
                for (int i = 0; i < rawBytes.length; i++) {
                    float val = Math.abs(rawBytes[i]) / 128.0f;
                    peaks[i] = Math.min(1.0f, val);
                }
                return peaks;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
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
        
        private double scale = 1.0;
        private double posX = 0.0;
        private double posY = 0.0;

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
        public String getAliasName() {
            if (aliasName != null) return aliasName;
            return mediaItem != null ? mediaItem.getFileName() : (textContent != null ? textContent : "Text Title");
        }
        public void setAliasName(String a) { this.aliasName = a; }
        public boolean isMuted() { return isMuted; }
        public void setMuted(boolean muted) { this.isMuted = muted; }
        
        public double getScale() { return scale; }
        public void setScale(double scale) { this.scale = scale; }
        public double getPosX() { return posX; }
        public void setPosX(double posX) { this.posX = posX; }
        public double getPosY() { return posY; }
        public void setPosY(double posY) { this.posY = posY; }
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
        ThemeManager.registerThemeChangeListener(theme -> {
            ThemeManager.applyTheme(rootPane);
            if (exportController != null && exportController.getRootPane() != null) {
                ThemeManager.applyTheme(exportController.getRootPane());
            }
        });
        
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

        if (previewContainer != null) {
            previewContainer.widthProperty().addListener((obs, oldW, newW) -> {
                double w = newW.doubleValue();
                double h = previewContainer.getHeight();
                if (w > 0 && h > 0) {
                    mediaView.setFitWidth(w);
                    mediaView.setFitHeight(h);
                    if (mediaViewV2 != null) {
                        mediaViewV2.setFitWidth(w);
                        mediaViewV2.setFitHeight(h);
                    }
                }
            });
            previewContainer.heightProperty().addListener((obs, oldH, newH) -> {
                double w = previewContainer.getWidth();
                double h = newH.doubleValue();
                if (w > 0 && h > 0) {
                    mediaView.setFitWidth(w);
                    mediaView.setFitHeight(h);
                    if (mediaViewV2 != null) {
                        mediaViewV2.setFitWidth(w);
                        mediaViewV2.setFitHeight(h);
                    }
                }
            });
        }

        tracks.add(new TimelineTrack("T1", TrackType.TEXT));
        tracks.add(new TimelineTrack("V1", TrackType.VIDEO));
        tracks.add(new TimelineTrack("V2", TrackType.VIDEO));
        tracks.add(new TimelineTrack("A1", TrackType.AUDIO));
        tracks.add(new TimelineTrack("A2", TrackType.AUDIO));
        
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
                } else if (clip == activeTimelineClipV2) {
                    mediaViewV2.setOpacity(newV.doubleValue());
                }
            }
        });
        scaleSlider.valueProperty().addListener((obs, oldV, newV) -> {
            TimelineClip clip = timelinePane.getSelectedClip();
            if (clip != null) {
                clip.setScale(newV.doubleValue());
                scaleValueLabel.setText(String.format("%.2fx", newV.doubleValue()));
                if (clip == activeTimelineClipV2) {
                    mediaViewV2.setScaleX(newV.doubleValue());
                    mediaViewV2.setScaleY(newV.doubleValue());
                } else if (clip == activeTimelineClip) {
                    mediaView.setScaleX(newV.doubleValue());
                    mediaView.setScaleY(newV.doubleValue());
                }
            }
        });
        posXSlider.valueProperty().addListener((obs, oldV, newV) -> {
            TimelineClip clip = timelinePane.getSelectedClip();
            if (clip != null) {
                clip.setPosX(newV.doubleValue());
                posXValueLabel.setText(String.format("%.0f", newV.doubleValue()));
                if (clip == activeTimelineClipV2) {
                    mediaViewV2.setTranslateX(newV.doubleValue());
                } else if (clip == activeTimelineClip) {
                    mediaView.setTranslateX(newV.doubleValue());
                }
            }
        });
        posYSlider.valueProperty().addListener((obs, oldV, newV) -> {
            TimelineClip clip = timelinePane.getSelectedClip();
            if (clip != null) {
                clip.setPosY(newV.doubleValue());
                posYValueLabel.setText(String.format("%.0f", newV.doubleValue()));
                if (clip == activeTimelineClipV2) {
                    mediaViewV2.setTranslateY(newV.doubleValue());
                } else if (clip == activeTimelineClip) {
                    mediaView.setTranslateY(newV.doubleValue());
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
        if (textOpacitySlider != null) {
            textOpacitySlider.valueProperty().addListener((obs, oldV, newV) -> {
                TimelineClip clip = timelinePane.getSelectedClip();
                if (clip != null && tracks.get(clip.getTrackIndex()).getType() == TrackType.TEXT) {
                    clip.setOpacity(newV.doubleValue());
                    if (textOpacityValueLabel != null) {
                        textOpacityValueLabel.setText((int)(newV.doubleValue() * 100) + "%");
                    }
                    if (previewTextOverlay != null && previewTextOverlay.isVisible()) {
                        previewTextOverlay.setOpacity(newV.doubleValue());
                    }
                }
            });
        }
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
        MenuItem addToV2 = new MenuItem("+ Add to V2 (Video PiP)");
        MenuItem addToA1 = new MenuItem("+ Add to A1 (Audio 1)");
        MenuItem addToA2 = new MenuItem("+ Add to A2 (Audio 2)");
        MenuItem removeFile = new MenuItem("X Remove from Pool");
        
        playPreview.setOnAction(e -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) loadAndPlayMedia(selected);
        });
        
        addToV1.setOnAction(e -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) addToTrackByType(selected, TrackType.VIDEO, 0);
        });
        addToV2.setOnAction(e -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) addToTrackByType(selected, TrackType.VIDEO, 1);
        });
        addToA1.setOnAction(e -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) addToTrackByType(selected, TrackType.AUDIO, 0);
        });
        addToA2.setOnAction(e -> {
            MediaItem selected = mediaListView.getSelectionModel().getSelectedItem();
            if (selected != null) addToTrackByType(selected, TrackType.AUDIO, 1);
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

        poolMenu.getItems().addAll(playPreview, addToV1, addToV2, addToA1, addToA2, new SeparatorMenuItem(), showInFM, new SeparatorMenuItem(), removeFile);
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
                if (textOpacitySlider != null) {
                    textOpacitySlider.setDisable(false);
                    textOpacitySlider.setValue(clip.getOpacity());
                    if (textOpacityValueLabel != null) {
                        textOpacityValueLabel.setText((int)(clip.getOpacity() * 100) + "%");
                    }
                }
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
                
                scaleSlider.setDisable(false);
                scaleSlider.setValue(clip.getScale());
                scaleValueLabel.setText(String.format("%.2fx", clip.getScale()));
                
                posXSlider.setDisable(false);
                posXSlider.setValue(clip.getPosX());
                posXValueLabel.setText(String.format("%.0f", clip.getPosX()));
                
                posYSlider.setDisable(false);
                posYSlider.setValue(clip.getPosY());
                posYValueLabel.setText(String.format("%.0f", clip.getPosY()));
                
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
        clip.setScale(1.0);
        clip.setPosX(0.0);
        clip.setPosY(0.0);
        updateInspector(clip);
        if (clip == activeTimelineClip && currentMediaPlayer != null) {
            currentMediaPlayer.setRate(1.0);
            mediaView.setOpacity(1.0);
            mediaView.setScaleX(1.0); mediaView.setScaleY(1.0);
            mediaView.setTranslateX(0.0); mediaView.setTranslateY(0.0);
        } else if (clip == activeTimelineClipV2 && currentPlayerV2 != null) {
            currentPlayerV2.setRate(1.0);
            mediaViewV2.setOpacity(1.0);
            mediaViewV2.setScaleX(1.0); mediaViewV2.setScaleY(1.0);
            mediaViewV2.setTranslateX(0.0); mediaViewV2.setTranslateY(0.0);
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
        File lastImportDir = WorkspaceStorage.getLastImportDirectory();
        if (lastImportDir != null && lastImportDir.exists()) {
            fc.setInitialDirectory(lastImportDir);
        }
        
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Media", "*.mp4", "*.mov", "*.mkv", "*.webm", "*.avi", "*.flv", "*.wmv", "*.mpg", "*.mpeg", "*.3gp", "*.mp3", "*.wav", "*.aac", "*.m4a", "*.ogg", "*.flac", "*.opus"),
            new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.mov", "*.mkv", "*.webm", "*.avi", "*.flv", "*.wmv", "*.mpg", "*.mpeg", "*.3gp"),
            new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.aac", "*.m4a", "*.ogg", "*.flac", "*.opus")
        );
        
        List<File> files = fc.showOpenMultipleDialog(rootPane.getScene().getWindow());
        if (files != null) {
            if (!files.isEmpty()) {
                File parentDir = files.get(0).getParentFile();
                if (parentDir != null && parentDir.exists()) {
                    WorkspaceStorage.setLastImportDirectory(parentDir);
                }
            }
            for (File file : files) {
                String nameLower = file.getName().toLowerCase();
                if (nameLower.endsWith(".mp4") || nameLower.endsWith(".mp3") || nameLower.endsWith(".wav") || nameLower.endsWith(".m4a")) {
                    mediaItems.add(new MediaItem(file));
                } else if (nameLower.endsWith(".aac")) {
                    File m4aFile = new File(file.getParentFile(), file.getName().substring(0, file.getName().lastIndexOf('.')) + ".m4a");
                    if (!m4aFile.exists()) {
                        try {
                            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-i", file.getAbsolutePath(), "-c:a", "copy", m4aFile.getAbsolutePath());
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
                } else if (isVideoFile(nameLower)) {
                    File mp4File = new File(file.getParentFile(), file.getName().substring(0, file.getName().lastIndexOf('.')) + ".mp4");
                    if (!mp4File.exists()) {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Converting " + file.getName() + " to MP4 for JavaFX compatibility. This might take a moment depending on the video length.", javafx.scene.control.ButtonType.OK);
                        ThemeManager.applyTheme(alert);
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
                } else if (isAudioFile(nameLower)) {
                    File mp3File = new File(file.getParentFile(), file.getName().substring(0, file.getName().lastIndexOf('.')) + ".mp3");
                    if (!mp3File.exists()) {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Converting " + file.getName() + " to MP3 for JavaFX compatibility.", javafx.scene.control.ButtonType.OK);
                        ThemeManager.applyTheme(alert);
                        alert.show();
                        new Thread(() -> {
                            try {
                                ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-i", file.getAbsolutePath(), "-c:a", "libmp3lame", "-q:a", "2", mp3File.getAbsolutePath());
                                Process p = pb.start();
                                p.waitFor();
                                Platform.runLater(() -> {
                                    alert.close();
                                    if (mp3File.exists()) {
                                        mediaItems.add(new MediaItem(mp3File));
                                    } else {
                                        showAlert("Conversion Failed", "Could not convert " + file.getName());
                                    }
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    } else {
                        mediaItems.add(new MediaItem(mp3File));
                    }
                } else {
                    mediaItems.add(new MediaItem(file));
                }
            }
        }
    }

    private boolean isVideoFile(String filename) {
        return filename.endsWith(".webm") || filename.endsWith(".mkv") || filename.endsWith(".mov") ||
               filename.endsWith(".avi") || filename.endsWith(".flv") || filename.endsWith(".wmv") ||
               filename.endsWith(".3gp") || filename.endsWith(".mpg") || filename.endsWith(".mpeg");
    }

    private boolean isAudioFile(String filename) {
        return filename.endsWith(".ogg") || filename.endsWith(".flac") || filename.endsWith(".wma") ||
               filename.endsWith(".m4r") || filename.endsWith(".opus") || filename.endsWith(".amr");
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
        activeTimelineClipV2 = null;
        activeAudioClips.clear();
        timelinePane.setPlayheadTime(0.0);
        if (currentMediaPlayer != null) {
            currentMediaPlayer.stop();
        }
        if (currentPlayerV2 != null) {
            currentPlayerV2.stop();
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
            previewTextOverlay.setOpacity(activeTextClip.getOpacity());
            previewTextOverlay.setVisible(true);
        } else if (previewTextOverlay != null) {
            previewTextOverlay.setVisible(false);
        }

        TimelineClip clipUnderPlayhead = null;
        if (tracks.size() > 1 && tracks.get(1).getType() == TrackType.VIDEO) {
            for (TimelineClip clip : tracks.get(1).getClips()) {
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
        
        // V2 Evaluation
        TimelineClip clipUnderPlayheadV2 = null;
        if (tracks.size() > 2 && tracks.get(2).getType() == TrackType.VIDEO) {
            for (TimelineClip clip : tracks.get(2).getClips()) {
                if (globalTime >= clip.getStartSeconds() && globalTime < clip.getStartSeconds() + clip.getDurationSeconds()) {
                    clipUnderPlayheadV2 = clip;
                    break;
                }
            }
        }
        if (clipUnderPlayheadV2 != activeTimelineClipV2) {
            activeTimelineClipV2 = clipUnderPlayheadV2;
            if (activeTimelineClipV2 != null) {
                loadAndPlayTimelineClipV2(activeTimelineClipV2, globalTime);
                Platform.runLater(() -> updateInspector(activeTimelineClipV2));
            } else {
                if (currentPlayerV2 != null) {
                    currentPlayerV2.stop();
                    mediaViewV2.setMediaPlayer(null);
                }
                mediaViewV2.setVisible(false);
                if (activeTimelineClip != null) {
                    Platform.runLater(() -> updateInspector(activeTimelineClip));
                }
            }
        } else if (activeTimelineClipV2 != null && currentPlayerV2 != null) {
            if (isPlayingTimeline) {
                if (currentPlayerV2.getStatus() != MediaPlayer.Status.PLAYING) currentPlayerV2.play();
            } else {
                double localTime = globalTime - activeTimelineClipV2.getStartSeconds();
                currentPlayerV2.seek(javafx.util.Duration.seconds(activeTimelineClipV2.getMediaStartOffset() + localTime));
            }
        }

        for (int trackIdx = 0; trackIdx < tracks.size(); trackIdx++) {
            if (tracks.get(trackIdx).getType() != TrackType.AUDIO) continue;
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
    
    private void loadAndPlayTimelineClipV2(TimelineClip clip, double globalTime) {
        if (currentPlayerV2 != null) {
            currentPlayerV2.stop();
            currentPlayerV2.dispose();
        }
        Media media = new Media(clip.getMediaItem().getFile().toURI().toString());
        currentPlayerV2 = new MediaPlayer(media);
        mediaViewV2.setMediaPlayer(currentPlayerV2);
        mediaViewV2.setVisible(true);

        currentPlayerV2.setVolume((isMuted || clip.isMuted()) ? 0 : volumeSlider.getValue());
        
        currentPlayerV2.setOnReady(() -> {
            double localTime = globalTime - clip.getStartSeconds();
            currentPlayerV2.seek(javafx.util.Duration.seconds(clip.getMediaStartOffset() + localTime));
            currentPlayerV2.setRate(clip.getPlaybackSpeed());
            mediaViewV2.setOpacity(clip.getOpacity());
            mediaViewV2.setScaleX(clip.getScale());
            mediaViewV2.setScaleY(clip.getScale());
            mediaViewV2.setTranslateX(clip.getPosX());
            mediaViewV2.setTranslateY(clip.getPosY());
            if (isPlayingTimeline) currentPlayerV2.play();
        });
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
            if (exportStage == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("export_dialog.fxml"));
                Parent root = loader.load();
                exportController = loader.getController();
                exportController.setMainWorkhorse(this);

                exportStage = new Stage();
                exportStage.setTitle("Advanced Export");
                javafx.scene.Scene scene = new javafx.scene.Scene(root);
                exportStage.setScene(scene);
                exportStage.initOwner(rootPane.getScene().getWindow());
                ThemeManager.applyTheme(root);
                
                javafx.geometry.Rectangle2D bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
                exportStage.setMaxWidth(bounds.getWidth());
                exportStage.setMaxHeight(bounds.getHeight());
                exportStage.sizeToScene();
                
                exportStage.setOnCloseRequest(e -> {
                    e.consume();
                    exportStage.hide();
                });
            }
            if (exportController != null && exportController.getRootPane() != null) {
                ThemeManager.applyTheme(exportController.getRootPane());
            }
            exportController.setTracks(tracks);
            exportStage.show();
            exportStage.toFront();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Could not open Export Dialog: " + e.getMessage());
        }
    }

    public void setRenderStatus(boolean rendering, boolean finished) {
        if (rendering) {
            btnRenderStatus.setVisible(true);
            btnRenderStatus.setManaged(true);
            btnRenderStatus.setText("Rendering... (Click to View)");
            btnRenderStatus.setStyle("-fx-background-color: #cb4b16; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        } else if (finished) {
            btnRenderStatus.setVisible(true);
            btnRenderStatus.setManaged(true);
            btnRenderStatus.setText("Render Finished! (Click to View)");
            btnRenderStatus.setStyle("-fx-background-color: #859900; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        } else {
            btnRenderStatus.setVisible(false);
            btnRenderStatus.setManaged(false);
        }
    }

    @FXML
    private void handleSaveAction() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Mediatar Project");
        File projDir = WorkspaceStorage.getProjectsDirectory();
        if (projDir != null && projDir.exists()) {
            fc.setInitialDirectory(projDir);
        }
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Mediatar Project Files (*.mediatar)", "*.mediatar"));
        File file = fc.showSaveDialog(rootPane.getScene().getWindow());
        if (file != null) {
            if (file.getParentFile() != null && file.getParentFile().exists()) {
                WorkspaceStorage.setProjectsDirectory(file.getParentFile());
            }
            // Enforce .mediatar extension regardless of what the user typed
            if (!file.getName().toLowerCase().endsWith(".mediatar")) {
                file = new File(file.getAbsolutePath() + ".mediatar");
            }
            try {
                ProjectSerializer.saveProject(file, mediaItems, tracks);
                showAlert("Success", "Project saved successfully to:\n" + file.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Save Error", "Failed to save project:\n" + e.getMessage());
            }
        }
    }

    @FXML
    private void clearTimeline() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Clear all clips from the timeline?\nYour imported media files will remain in the pool.",
            ButtonType.YES, ButtonType.CANCEL);
        confirm.setTitle("Clear Timeline");
        confirm.setHeaderText(null);
        ThemeManager.applyTheme(confirm);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) return;

        stopTimeline();
        for (TimelineTrack track : tracks) {
            track.getClips().clear();
        }
        updateInspector(null);
        globalPlayheadTime = 0.0;
        timelinePane.setPlayheadTime(0.0);
        if (lblLargeTimecode != null) lblLargeTimecode.setText(formatTimecode(0.0));
        timelinePane.redraw();
    }

    @FXML
    private void handleLoadAction() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open Mediatar Project");
        File projDir = WorkspaceStorage.getProjectsDirectory();
        if (projDir != null && projDir.exists()) {
            fc.setInitialDirectory(projDir);
        }
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Mediatar Project Files (*.mediatar)", "*.mediatar"));
        File file = fc.showOpenDialog(rootPane.getScene().getWindow());
        if (file != null) {
            if (file.getParentFile() != null && file.getParentFile().exists()) {
                WorkspaceStorage.setProjectsDirectory(file.getParentFile());
            }
            try {
                stopTimeline();

                ProjectSerializer.LoadedProject loaded = ProjectSerializer.loadProject(file);

                // Clear current workspace
                mediaItems.clear();
                for (TimelineTrack track : tracks) {
                    track.getClips().clear();
                }

                // Restore Media Pool
                List<String> missingFiles = new ArrayList<>();
                for (File f : loaded.mediaPoolFiles) {
                    if (!f.exists()) {
                        missingFiles.add(f.getName());
                    }
                    mediaItems.add(new MediaItem(f));
                }

                // Restore Tracks and Clips
                for (ProjectSerializer.LoadedTrack loadedTrack : loaded.tracks) {
                    // Find corresponding track
                    TimelineTrack targetTrack = null;
                    for (TimelineTrack t : tracks) {
                        if (t.getName().equals(loadedTrack.name)) {
                            targetTrack = t;
                            break;
                        }
                    }
                    if (targetTrack == null) continue;

                    for (ProjectSerializer.LoadedClip loadedClip : loadedTrack.clips) {
                        MediaItem mediaItem = null;
                        if (loadedClip.filePath != null) {
                            // Find in media pool
                            for (MediaItem item : mediaItems) {
                                if (item.getFile().getAbsolutePath().equals(loadedClip.filePath)) {
                                    mediaItem = item;
                                    break;
                                }
                            }
                            // Fallback if not found in mediaPoolFiles (shouldn't happen but just in case)
                            if (mediaItem == null) {
                                File clipFile = new File(loadedClip.filePath);
                                if (!clipFile.exists()) {
                                    missingFiles.add(clipFile.getName());
                                }
                                mediaItem = new MediaItem(clipFile);
                                mediaItems.add(mediaItem);
                            }
                        }

                        TimelineClip clip;
                        if (targetTrack.getType() == TrackType.TEXT) {
                            clip = new TimelineClip(loadedClip.textContent, loadedClip.start, loadedClip.duration, tracks.indexOf(targetTrack));
                            clip.setTextColor(loadedClip.textColor);
                            clip.setFontSize(loadedClip.fontSize);
                            clip.setOpacity(loadedClip.opacity);
                        } else {
                            clip = new TimelineClip(mediaItem, loadedClip.start, loadedClip.duration, tracks.indexOf(targetTrack), loadedClip.offset);
                            clip.setPlaybackSpeed(loadedClip.speed);
                            clip.setOpacity(loadedClip.opacity);
                            clip.setScale(loadedClip.scale);
                            clip.setPosX(loadedClip.posX);
                            clip.setPosY(loadedClip.posY);
                        }
                        clip.setAliasName(loadedClip.aliasName);
                        clip.setMuted(loadedClip.isMuted);

                        targetTrack.getClips().add(clip);
                    }
                }

                // Reset and refresh workspace state
                updateInspector(null);
                timelinePane.redraw();

                globalPlayheadTime = 0.0;
                timelinePane.setPlayheadTime(0.0);
                evaluateTimelinePlayback(0.0);
                if (lblLargeTimecode != null) {
                    lblLargeTimecode.setText(formatTimecode(0.0));
                }

                if (!missingFiles.isEmpty()) {
                    showAlert("Warning", "Project loaded, but the following files were not found on disk:\n" 
                        + String.join("\n", missingFiles) + "\n\nPaths might need to be resolved for playback.");
                } else {
                    showAlert("Success", "Project loaded successfully!");
                }

            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Load Error", "Failed to load project:\n" + e.getMessage());
            }
        }
    }

    @FXML private void handleExit() { Platform.exit(); System.exit(0); }
    
    @FXML private void applySolarizedTheme() { ThemeManager.setTheme(ThemeManager.Theme.SOLARIZED); }
    @FXML private void applyDarkTheme() { ThemeManager.setTheme(ThemeManager.Theme.DARK); }
    @FXML private void applyLightTheme() { ThemeManager.setTheme(ThemeManager.Theme.LIGHT); }
    
    private void showAlert(String title, String msg) { 
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        ThemeManager.applyTheme(alert);
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

    @FXML
    private void handleAboutAction() {
        Stage stage = new Stage();
        stage.setTitle("About MMediatar");
        stage.initOwner(rootPane.getScene().getWindow());
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        
        BorderPane layout = new BorderPane();
        layout.setPadding(new javafx.geometry.Insets(20));
        ThemeManager.applyTheme(layout);
        
        // --- Header ---
        VBox header = new VBox(5);
        header.setAlignment(javafx.geometry.Pos.CENTER);
        
        Label titleLabel = new Label("MMediatar");
        titleLabel.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 26));
        titleLabel.setStyle("-fx-text-fill: #586e75;");
        
        Label versionLabel = new Label("Version 2.4.0 (Stable)");
        versionLabel.setFont(javafx.scene.text.Font.font("System", 13));
        versionLabel.setStyle("-fx-text-fill: #657b83;");
        
        header.getChildren().addAll(titleLabel, versionLabel);
        layout.setTop(header);
        
        // --- Center Panel ---
        VBox centerPanel = new VBox(12);
        centerPanel.setAlignment(javafx.geometry.Pos.CENTER);
        centerPanel.setPadding(new javafx.geometry.Insets(15, 0, 15, 0));
        
        // App Description
        VBox descBox = new VBox(5);
        descBox.setMaxWidth(480);
        descBox.setPadding(new javafx.geometry.Insets(12));
        descBox.setStyle("-fx-border-color: #93a1a1; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-color: #eee8d5;");
        
        Label descTitle = new Label("About MMediatar:");
        descTitle.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 13));
        descTitle.setStyle("-fx-text-fill: #2aa198;");
        
        Label descBody = new Label("MMediatar is a lightweight JavaFX-based video editor. It allows you to arrange video, audio, and text clips on a multi-track timeline, preview them in real-time using JavaFX MediaViews, and export the final composition using native FFmpeg commands.");
        descBody.setWrapText(true);
        descBody.setStyle("-fx-text-fill: #586e75;");
        
        descBox.getChildren().addAll(descTitle, descBody);

        VBox depBox = new VBox(8);
        depBox.setMaxWidth(480);
        depBox.setPadding(new javafx.geometry.Insets(12));
        depBox.setStyle("-fx-border-color: #93a1a1; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-color: #eee8d5;");
        
        Label depTitle = new Label("Native Core Dependencies & Capabilities:");
        depTitle.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 13));
        depTitle.setStyle("-fx-text-fill: #859900;");
        depBox.getChildren().add(depTitle);
        
        boolean hasFFmpeg = isCommandAvailable("ffmpeg");
        boolean hasFFprobe = isCommandAvailable("ffprobe");
        String fileManager = detectFileManager();
        boolean hasFileManager = fileManager != null && !fileManager.isEmpty();
        
        Label ffmpegLabel = new Label((hasFFmpeg ? "✔" : "❌") + " FFmpeg : Transcodes media, generates waveforms, and renders final video exports.");
        ffmpegLabel.setWrapText(true);
        ffmpegLabel.setStyle("-fx-text-fill: #586e75;");
        
        Label ffprobeLabel = new Label((hasFFprobe ? "✔" : "❌") + " FFprobe : Analyzes media streams, validates metadata, and extracts durations.");
        ffprobeLabel.setWrapText(true);
        ffprobeLabel.setStyle("-fx-text-fill: #586e75;");
        
        Label fmLabel = new Label((hasFileManager ? "✔" : "❌") + " File Manager (" + fileManager + ") : Reveals exported files natively in your OS.");
        fmLabel.setWrapText(true);
        fmLabel.setStyle("-fx-text-fill: #586e75;");
        
        depBox.getChildren().addAll(ffmpegLabel, ffprobeLabel, fmLabel);
        
        Label sysInfo = new Label("Release: #2026.05.21-REV4 (May 21, 2026)");
        sysInfo.setFont(javafx.scene.text.Font.font("Consolas", 12));
        sysInfo.setStyle("-fx-text-fill: #93a1a1;");
        
        centerPanel.getChildren().addAll(descBox, depBox, sysInfo);
        layout.setCenter(centerPanel);
        
        // --- Bottom ---
        HBox bottomPanel = new HBox();
        bottomPanel.setAlignment(javafx.geometry.Pos.CENTER);
        
        Button btnClose = new Button("Close");
        btnClose.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 13));
        btnClose.setStyle("-fx-background-color: #859900; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 8 20 8 20; -fx-background-radius: 4;");
        btnClose.setOnAction(e -> stage.close());
        
        bottomPanel.getChildren().add(btnClose);
        layout.setBottom(bottomPanel);
        
        javafx.scene.Scene scene = new javafx.scene.Scene(layout, 520, 360);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.showAndWait();
    }

    private boolean isCommandAvailable(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"which", cmd});
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}