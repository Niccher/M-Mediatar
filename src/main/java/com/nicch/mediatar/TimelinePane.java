package com.nicch.mediatar;

import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class TimelinePane extends Region {
    private final Canvas canvas;
    private List<MainWorkhorse.TimelineTrack> tracks;
    
    private double zoomSecondsPerPixel = 0.05; 
    private double playheadTime = 0.0;
    private MainWorkhorse.TimelineClip selectedClip = null;
    
    private Consumer<MainWorkhorse.TimelineClip> onClipSelected;
    private Runnable onPlayPauseRequested; // Callback for the context menu
    private Consumer<Double> onPlayheadScrubbed;
    
    private Tooltip hoverMetadataTooltip;
    private ContextMenu rightClickMenu;

    private boolean isSnappingEnabled = true;
    private double currentZoomLevel = 50.0;

    public enum ToolMode { SELECTION, BLADE }
    private ToolMode currentTool = ToolMode.SELECTION;

    private enum DragMode { NONE, MOVE_CLIP, RESIZE_LEFT, RESIZE_RIGHT, SCRUB_PLAYHEAD }
    private DragMode dragMode = DragMode.NONE;
    
    private double dragStartMouseX, dragStartClipStart, dragStartClipDur, dragStartClipOffset;
    private MainWorkhorse.TimelineClip draggedClip;
    
    private final double RULER_HEIGHT = 30.0;
    private final double SNAP_THRESHOLD = 0.2; 

    public TimelinePane() {
        canvas = new Canvas();
        canvas.setFocusTraversable(true);
        getChildren().add(canvas);
        
        setupTooltipsAndMenus();

        widthProperty().addListener(evt -> redraw());
        heightProperty().addListener(evt -> redraw());

        canvas.setOnMouseMoved(this::onMouseMoved);
        canvas.setOnMousePressed(this::onMousePressed);
        canvas.setOnMouseDragged(this::onMouseDragged);
        
        canvas.setOnMouseExited(e -> hoverMetadataTooltip.hide());
        
        canvas.setOnMouseReleased(e -> {
            dragMode = DragMode.NONE;
            draggedClip = null;
            canvas.setCursor(currentTool == ToolMode.BLADE ? Cursor.CROSSHAIR : Cursor.DEFAULT);
            redraw();
        });

        canvas.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });

        canvas.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasString()) {
                String path = db.getString();
                if (path.startsWith("file://")) path = path.replace("file://", "");
                
                File file = new File(path);
                if (file.exists()) {
                    if(getMaxTime() == 0) zoomSecondsPerPixel = 10.0 / Math.max(1, getWidth());
                    
                    double dropTime = e.getX() * zoomSecondsPerPixel;
                    int targetTrack = getTrackIndexAtY(e.getY());
                    // Don't allow dropping media files onto the TEXT track
                    if (targetTrack >= 0 && tracks.get(targetTrack).getType() == MainWorkhorse.TrackType.TEXT) {
                        // Find the first VIDEO or AUDIO track instead
                        targetTrack = -1;
                        for (int i = 0; i < tracks.size(); i++) {
                            if (tracks.get(i).getType() != MainWorkhorse.TrackType.TEXT) {
                                targetTrack = i;
                                break;
                            }
                        }
                    }
                    if (targetTrack >= 0) {
                        MainWorkhorse.MediaItem item = new MainWorkhorse.MediaItem(file);
                        double finalDropTime = Math.max(0, dropTime);
                        boolean adjusted;
                        do {
                            adjusted = false;
                            for (MainWorkhorse.TimelineClip existing : tracks.get(targetTrack).getClips()) {
                                double exStart = existing.getStartSeconds();
                                double exEnd = exStart + existing.getDurationSeconds();
                                if (finalDropTime >= exStart - 0.2 && finalDropTime < exEnd) {
                                    finalDropTime = exEnd;
                                    adjusted = true;
                                }
                            }
                        } while (adjusted);
                        addClipExternal(item, finalDropTime, targetTrack);
                        e.setDropCompleted(true);
                    }
                }
            }
            e.consume();
        });
    }
    
    public void setOnPlayPauseRequested(Runnable callback) {
        this.onPlayPauseRequested = callback;
    }
    
    public void setOnPlayheadScrubbed(Consumer<Double> callback) {
        this.onPlayheadScrubbed = callback;
    }
    
    private void setupTooltipsAndMenus() {
        hoverMetadataTooltip = new Tooltip();
        hoverMetadataTooltip.setFont(Font.font("System", 12));
        
        rightClickMenu = new ContextMenu();
        
        MenuItem playItem = new MenuItem("> Play / Pause");
        MenuItem splitAvItem = new MenuItem("-- Detach Audio");
        MenuItem muteItem = new MenuItem("🔇 Toggle Mute");
        MenuItem trimItem = new MenuItem("[] Trim (Cut 1s from ends)");
        MenuItem mergeItem = new MenuItem("++ Merge with next on track");
        MenuItem removeItem = new MenuItem("X  Remove Clip");
        
        playItem.setOnAction(e -> {
           if(onPlayPauseRequested != null) onPlayPauseRequested.run();
        });
        
        splitAvItem.setOnAction(e -> {
            if (selectedClip != null) {
                // Find which track V1 (VIDEO) is on
                int videoTrackIdx = -1;
                int audioTrackIdx = -1;
                for (int i = 0; i < tracks.size(); i++) {
                    if (tracks.get(i).getType() == MainWorkhorse.TrackType.VIDEO && videoTrackIdx == -1) videoTrackIdx = i;
                    if (tracks.get(i).getType() == MainWorkhorse.TrackType.AUDIO && audioTrackIdx == -1) audioTrackIdx = i;
                }
                if (selectedClip.getTrackIndex() == videoTrackIdx && audioTrackIdx >= 0) {
                    MainWorkhorse.TimelineClip audioExt = new MainWorkhorse.TimelineClip(
                        selectedClip.getMediaItem(), selectedClip.getStartSeconds(), selectedClip.getDurationSeconds(), audioTrackIdx, selectedClip.getMediaStartOffset()
                    );
                    String origName = selectedClip.getMediaItem().getFileName();
                    int dotIdx = origName.lastIndexOf('.');
                    String audioName = (dotIdx > 0 ? origName.substring(0, dotIdx) : origName) + " (Audio).mp3";
                    audioExt.setAliasName(audioName);
                    
                    tracks.get(audioTrackIdx).getClips().add(audioExt);
                    if (audioExt.getMediaItem() != null) {
                        audioExt.getMediaItem().addOnLoadCallback(this::redraw);
                    }
                    selectedClip.setMuted(true);
                    redraw();
                }
            }
        });
        
        muteItem.setOnAction(e -> {
            if (selectedClip != null) {
                selectedClip.setMuted(!selectedClip.isMuted());
                redraw();
            }
        });
        
        trimItem.setOnAction(e -> {
            if (selectedClip != null && selectedClip.getDurationSeconds() > 2.0) {
                selectedClip.setStartSeconds(selectedClip.getStartSeconds() + 1.0);
                selectedClip.setDurationSeconds(selectedClip.getDurationSeconds() - 2.0);
                selectedClip.setMediaStartOffset(selectedClip.getMediaStartOffset() + 1.0);
                redraw();
            }
        });
        
        mergeItem.setOnAction(e -> {
            if (selectedClip != null) {
                MainWorkhorse.TimelineTrack track = tracks.get(selectedClip.getTrackIndex());
                for (MainWorkhorse.TimelineClip other : track.getClips()) {
                    if (other != selectedClip && Math.abs(selectedClip.getStartSeconds() + selectedClip.getDurationSeconds() - other.getStartSeconds()) < 0.5) {
                        selectedClip.setDurationSeconds(selectedClip.getDurationSeconds() + other.getDurationSeconds());
                        track.getClips().remove(other);
                        redraw();
                        break;
                    }
                }
            }
        });
        
        removeItem.setOnAction(e -> deleteSelectedClip());
        
        rightClickMenu.getItems().addAll(playItem, splitAvItem, muteItem, trimItem, mergeItem, new SeparatorMenuItem(), removeItem);
    }

    public void setTracks(List<MainWorkhorse.TimelineTrack> tracks) {
        this.tracks = tracks;
        if (tracks != null) {
            for (MainWorkhorse.TimelineTrack track : tracks) {
                for (MainWorkhorse.TimelineClip clip : track.getClips()) {
                    if (clip.getMediaItem() != null) {
                        clip.getMediaItem().addOnLoadCallback(this::redraw);
                    }
                }
            }
        }
        redraw();
    }
    
    public void setToolMode(ToolMode mode) {
        this.currentTool = mode;
        canvas.setCursor(mode == ToolMode.BLADE ? Cursor.CROSSHAIR : Cursor.DEFAULT);
    }
    
    public void setSnappingEnabled(boolean snap) {
        this.isSnappingEnabled = snap;
    }

    public void setZoomLevel(double zoom) {
        this.currentZoomLevel = zoom;
        redraw();
    }
    
    public void setOnClipSelected(Consumer<MainWorkhorse.TimelineClip> listener) {
        this.onClipSelected = listener;
    }

    public void setPlayheadTime(double time) {
        this.playheadTime = Math.max(0, time);
        redraw();
    }
    
    public void scrubPlayhead(double time) {
        setPlayheadTime(time);
        if (onPlayheadScrubbed != null) onPlayheadScrubbed.accept(this.playheadTime);
    }
    
    public MainWorkhorse.TimelineClip getSelectedClip() { return selectedClip; }

    public double getMaxTime() {
        double maxTime = 0;
        if (tracks != null) {
            for (MainWorkhorse.TimelineTrack track : tracks) {
                for (MainWorkhorse.TimelineClip clip : track.getClips()) {
                    maxTime = Math.max(maxTime, clip.getStartSeconds() + clip.getDurationSeconds());
                }
            }
        }
        return maxTime;
    }

    public void redraw() {
        if (tracks == null) return;
        double w = getWidth();
        double h = getHeight();
        canvas.setWidth(w);
        canvas.setHeight(h);
        
        double maxTimelineDuration = Math.max(10.0, getMaxTime());
        double baseZoomSecondsPerPixel = (maxTimelineDuration * 1.1) / Math.max(1, w);
        double maxZoomSecondsPerPixel = 0.02; // very zoomed in
        double fraction = Math.max(0, Math.min(1.0, (currentZoomLevel - 1.0) / 99.0));
        
        // Logarithmic scale for smoother zooming experience
        zoomSecondsPerPixel = baseZoomSecondsPerPixel * Math.pow((maxZoomSecondsPerPixel / baseZoomSecondsPerPixel), fraction);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        double trackH = getTrackHeight();

        for (int i = 0; i < tracks.size(); i++) {
            MainWorkhorse.TimelineTrack track = tracks.get(i);
            double y = RULER_HEIGHT + (i * trackH);
            
            gc.setFill(Color.rgb(100, 100, 100, 0.2)); 
            gc.fillRect(0, y, w, trackH);
            
            gc.setStroke(Color.rgb(120, 120, 120, 0.5));
            gc.strokeLine(0, y + trackH, w, y + trackH);
            
            gc.setFill(ThemeManager.currentTheme == ThemeManager.Theme.DARK ? Color.LIGHTGRAY : Color.DARKGRAY);
            gc.fillText(track.getName(), 10, y + 20);

            for (MainWorkhorse.TimelineClip clip : track.getClips()) {
                drawClip(gc, clip, track.getType(), y, trackH);
            }
        }

        drawRulerAndPlayhead(gc, w, h, maxTimelineDuration);
    }

    private void drawClip(GraphicsContext gc, MainWorkhorse.TimelineClip clip, MainWorkhorse.TrackType type, double y, double trackH) {
        double x = clip.getStartSeconds() / zoomSecondsPerPixel;
        double clipW = clip.getDurationSeconds() / zoomSecondsPerPixel;
        if (x + clipW < 0 || x > getWidth()) return; 

        Color baseColor = Color.web("#2980b9");
        if (type == MainWorkhorse.TrackType.AUDIO) baseColor = Color.web("#27ae60");
        else if (type == MainWorkhorse.TrackType.TEXT) baseColor = Color.web("#8e44ad");
        
        if (clip == selectedClip) {
            gc.setFill(baseColor.brighter());
            gc.setStroke(Color.WHITE);
        } else {
            gc.setFill(baseColor);
            gc.setStroke(baseColor.darker());
        }

        gc.fillRect(x, y + 4, clipW, trackH - 8);

        // Draw Audio Waveform inside audio clip if peaks data is available
        if (type == MainWorkhorse.TrackType.AUDIO && clip.getMediaItem() != null) {
            float[] peaks = clip.getMediaItem().getAudioPeaks();
            if (peaks != null && peaks.length > 0) {
                if (clip == selectedClip) {
                    gc.setStroke(Color.rgb(255, 255, 255, 0.85));
                } else {
                    gc.setStroke(Color.rgb(200, 255, 200, 0.7));
                }
                gc.setLineWidth(1.0);
                
                double centerY = y + trackH / 2.0;
                double maxAmplitudeH = trackH - 16.0; // padding inside the block
                
                for (double px = 0; px < clipW; px += 1.0) {
                    double timeInClip = px * zoomSecondsPerPixel;
                    double mediaTime = clip.getMediaStartOffset() + timeInClip;
                    int peakIdx = (int) (mediaTime * 50.0);
                    
                    if (peakIdx >= 0 && peakIdx < peaks.length) {
                        float peakVal = peaks[peakIdx];
                        double halfBarH = (peakVal * maxAmplitudeH) / 2.0;
                        gc.strokeLine(x + px, centerY - halfBarH, x + px, centerY + halfBarH);
                    }
                }
            }
        }

        gc.setLineWidth(1.5);
        gc.strokeRect(x, y + 4, clipW, trackH - 8);

        gc.setFill(Color.WHITE);
        String label = clip.getAliasName();
        if (clip.isMuted()) label = "🔇 " + label;
        gc.fillText(label, x + 5, y + (trackH / 2) + 4);
        
        gc.setFill(Color.rgb(0, 0, 0, 0.3));
        gc.fillRect(x, y + 4, 6, trackH - 8);
        gc.fillRect(x + clipW - 6, y + 4, 6, trackH - 8);
    }

    private void drawRulerAndPlayhead(GraphicsContext gc, double w, double h, double maxTime) {
        gc.setFill(Color.rgb(100, 100, 100, 0.3));
        gc.fillRect(0, 0, w, RULER_HEIGHT);
        
        gc.setFill(ThemeManager.currentTheme == ThemeManager.Theme.DARK ? Color.WHITE : Color.BLACK);
        int step = Math.max(1, (int)(maxTime / 10)); 
        for(int i = 0; i <= maxTime * 1.1; i += step) {
             double xPos = i / zoomSecondsPerPixel;
             gc.fillText(i + "s", xPos + 2, RULER_HEIGHT - 5);
             gc.strokeLine(xPos, RULER_HEIGHT - 10, xPos, RULER_HEIGHT);
        }
        
        double playheadX = playheadTime / zoomSecondsPerPixel;
        gc.setStroke(Color.RED);
        gc.setLineWidth(1.5);
        gc.strokeLine(playheadX, RULER_HEIGHT, playheadX, h);
        
        gc.setFill(Color.RED);
        gc.fillPolygon(
            new double[]{playheadX - 6, playheadX + 6, playheadX, playheadX},
            new double[]{RULER_HEIGHT - 10, RULER_HEIGHT - 10, RULER_HEIGHT, RULER_HEIGHT},
            4
        );
    }

    private void onMouseMoved(MouseEvent e) {
        if (currentTool == ToolMode.BLADE) {
            canvas.setCursor(Cursor.CROSSHAIR);
            hoverMetadataTooltip.hide();
            return;
        }

        MainWorkhorse.TimelineClip hovered = getClipAt(e.getX(), e.getY());
        
        if (hovered != null) {
            String clipLabel = (hovered.getMediaItem() != null) ? hovered.getMediaItem().getFileName() : hovered.getTextContent();
            hoverMetadataTooltip.setText(String.format("Clip: %s\nStart: %.2fs\nDuration: %.2fs\nTrack: %d", 
                clipLabel != null ? clipLabel : "Text", hovered.getStartSeconds(), hovered.getDurationSeconds(), hovered.getTrackIndex()));
            
            if (!hoverMetadataTooltip.isShowing()) {
                hoverMetadataTooltip.show(canvas, e.getScreenX() + 15, e.getScreenY() + 15);
            } else {
                hoverMetadataTooltip.setX(e.getScreenX() + 15);
                hoverMetadataTooltip.setY(e.getScreenY() + 15);
            }
        } else {
            hoverMetadataTooltip.hide();
        }

        if (e.getY() > RULER_HEIGHT && hovered != null) {
            double clipStartX = hovered.getStartSeconds() / zoomSecondsPerPixel;
            double clipEndX = (hovered.getStartSeconds() + hovered.getDurationSeconds()) / zoomSecondsPerPixel;
            double mouseX = e.getX();
            if (Math.abs(mouseX - clipStartX) <= 8.0 || Math.abs(mouseX - clipEndX) <= 8.0) {
                canvas.setCursor(Cursor.H_RESIZE);
                return;
            }
        }
        canvas.setCursor(Cursor.DEFAULT);
    }

    private void onMousePressed(MouseEvent e) {
        canvas.requestFocus();
        rightClickMenu.hide(); 
        
        double time = e.getX() * zoomSecondsPerPixel;
        
        if (e.getY() <= RULER_HEIGHT) {
            dragMode = DragMode.SCRUB_PLAYHEAD;
            scrubPlayhead(time);
            return;
        }

        MainWorkhorse.TimelineClip clicked = getClipAt(e.getX(), e.getY());
        
        if (e.isSecondaryButtonDown()) {
            if (clicked != null) {
                selectClip(clicked);
                rightClickMenu.show(canvas, e.getScreenX(), e.getScreenY());
            }
            return;
        }
        
        if (currentTool == ToolMode.BLADE) {
            if (clicked != null) executeSplit(clicked, time);
            return;
        }
        
        selectClip(clicked);
        
        if (clicked != null) {
            draggedClip = clicked;
            dragStartMouseX = e.getX();
            dragStartClipStart = clicked.getStartSeconds();
            dragStartClipDur = clicked.getDurationSeconds();
            dragStartClipOffset = clicked.getMediaStartOffset();

            double clipStartX = clicked.getStartSeconds() / zoomSecondsPerPixel;
            double clipEndX = (clicked.getStartSeconds() + clicked.getDurationSeconds()) / zoomSecondsPerPixel;
            double mouseX = e.getX();

            // Only enter resize mode if within 8 pixels of either edge
            if (Math.abs(mouseX - clipStartX) <= 8.0) {
                dragMode = DragMode.RESIZE_LEFT;
            } else if (Math.abs(mouseX - clipEndX) <= 8.0) {
                dragMode = DragMode.RESIZE_RIGHT;
            } else {
                dragMode = DragMode.MOVE_CLIP;
            }
        }
    }

    private void onMouseDragged(MouseEvent e) {
        double time = e.getX() * zoomSecondsPerPixel;

        if (dragMode == DragMode.SCRUB_PLAYHEAD) {
            scrubPlayhead(time);
            return;
        }

        if (draggedClip == null || currentTool != ToolMode.SELECTION) return;
        
        double deltaTime = (e.getX() - dragStartMouseX) * zoomSecondsPerPixel;

        switch (dragMode) {
            case MOVE_CLIP:
                double newStart = dragStartClipStart + deltaTime;
                newStart = applySnapMagnetism(newStart, draggedClip);
                if (newStart >= 0) {
                    int newTrackIdx = getTrackIndexAtY(e.getY());
                    int targetTrackIdx = (newTrackIdx >= 0 && newTrackIdx < tracks.size() &&
                        tracks.get(newTrackIdx).getType() == tracks.get(draggedClip.getTrackIndex()).getType())
                        ? newTrackIdx : draggedClip.getTrackIndex();

                    // Collision avoidance on the same track: prevent overlapping clips
                    for (MainWorkhorse.TimelineClip other : tracks.get(targetTrackIdx).getClips()) {
                        if (other == draggedClip) continue;
                        double otherStart = other.getStartSeconds();
                        double otherEnd = otherStart + other.getDurationSeconds();
                        if (newStart < otherEnd && newStart + draggedClip.getDurationSeconds() > otherStart) {
                            if (dragStartClipStart >= otherEnd - 0.1) {
                                newStart = Math.max(newStart, otherEnd);
                            } else if (dragStartClipStart + dragStartClipDur <= otherStart + 0.1) {
                                newStart = Math.min(newStart, otherStart - draggedClip.getDurationSeconds());
                            } else {
                                double otherMid = (otherStart + otherEnd) / 2.0;
                                double clipMid = newStart + draggedClip.getDurationSeconds() / 2.0;
                                if (clipMid >= otherMid) {
                                    newStart = otherEnd;
                                } else {
                                    newStart = Math.max(0, otherStart - draggedClip.getDurationSeconds());
                                }
                            }
                        }
                    }
                    newStart = Math.max(0, newStart);
                    draggedClip.setStartSeconds(newStart);

                    if (targetTrackIdx != draggedClip.getTrackIndex()) {
                        tracks.get(draggedClip.getTrackIndex()).getClips().remove(draggedClip);
                        draggedClip.setTrackIndex(targetTrackIdx);
                        tracks.get(targetTrackIdx).getClips().add(draggedClip);
                    }
                }
                break;
            case RESIZE_LEFT:
                double newStartL = dragStartClipStart + deltaTime;
                newStartL = applySnapMagnetism(newStartL, draggedClip);
                double offsetShift = newStartL - dragStartClipStart;
                // Limit offsetShift so mediaStartOffset cannot go below 0
                if (dragStartClipOffset + offsetShift < 0) {
                    offsetShift = -dragStartClipOffset;
                    newStartL = dragStartClipStart + offsetShift;
                }
                double newDur = dragStartClipDur - offsetShift;
                if (newDur > 0.5 && newStartL >= 0) {
                    draggedClip.setStartSeconds(newStartL);
                    draggedClip.setDurationSeconds(newDur);
                    draggedClip.setMediaStartOffset(Math.max(0, dragStartClipOffset + offsetShift));
                }
                break;
            case RESIZE_RIGHT:
                double newDurR = dragStartClipDur + deltaTime;
                double potentialEnd = draggedClip.getStartSeconds() + newDurR;
                double snappedEnd = applySnapMagnetism(potentialEnd, draggedClip);
                newDurR = snappedEnd - draggedClip.getStartSeconds();
                if (draggedClip.getMediaItem() != null && draggedClip.getMediaItem().getDurationSeconds() > 0) {
                    double maxDur = draggedClip.getMediaItem().getDurationSeconds() - draggedClip.getMediaStartOffset();
                    if (maxDur > 0.5) newDurR = Math.min(newDurR, maxDur);
                }
                if (newDurR > 0.5) draggedClip.setDurationSeconds(newDurR);
                break;
        }
        redraw();
    }

    private void executeSplit(MainWorkhorse.TimelineClip clip, double splitTime) {
        if (splitTime <= clip.getStartSeconds() || splitTime >= clip.getStartSeconds() + clip.getDurationSeconds()) return;

        double leftDur = splitTime - clip.getStartSeconds();
        double rightDur = clip.getDurationSeconds() - leftDur;

        MainWorkhorse.TimelineClip right = new MainWorkhorse.TimelineClip(
                clip.getMediaItem(), splitTime, rightDur, clip.getTrackIndex(), clip.getMediaStartOffset() + leftDur);
        clip.setDurationSeconds(leftDur);
        tracks.get(clip.getTrackIndex()).getClips().add(right);
        if (right.getMediaItem() != null) {
            right.getMediaItem().addOnLoadCallback(this::redraw);
        }
        redraw();
    }

    private double applySnapMagnetism(double time, MainWorkhorse.TimelineClip ignoreClip) {
        if (!isSnappingEnabled) return time;
        if (Math.abs(time - playheadTime) < SNAP_THRESHOLD) return playheadTime;
        for (MainWorkhorse.TimelineTrack track : tracks) {
            for (MainWorkhorse.TimelineClip c : track.getClips()) {
                if (c == ignoreClip) continue;
                if (Math.abs(time - c.getStartSeconds()) < SNAP_THRESHOLD) return c.getStartSeconds();
                if (Math.abs(time - (c.getStartSeconds() + c.getDurationSeconds())) < SNAP_THRESHOLD) 
                    return c.getStartSeconds() + c.getDurationSeconds();
            }
        }
        return time;
    }

    private MainWorkhorse.TimelineClip getClipAt(double x, double y) {
        int trackIdx = getTrackIndexAtY(y);
        if (trackIdx < 0 || trackIdx >= tracks.size()) return null;
        
        double time = x * zoomSecondsPerPixel;
        List<MainWorkhorse.TimelineClip> clips = tracks.get(trackIdx).getClips();
        for (int i = clips.size() - 1; i >= 0; i--) {
            MainWorkhorse.TimelineClip clip = clips.get(i);
            if (time >= clip.getStartSeconds() && time <= clip.getStartSeconds() + clip.getDurationSeconds()) {
                return clip;
            }
        }
        return null;
    }

    private int getTrackIndexAtY(double y) {
        if (y <= RULER_HEIGHT || tracks == null || tracks.isEmpty()) return -1;
        int idx = (int) ((y - RULER_HEIGHT) / getTrackHeight());
        return Math.min(idx, tracks.size() - 1);
    }

    private double getTrackHeight() {
        return (getHeight() - RULER_HEIGHT) / Math.max(1, tracks == null ? 1 : tracks.size());
    }

    public void addClipExternal(MainWorkhorse.MediaItem item, double startTime, int trackIdx) {
        double dur = (item != null) ? item.getDurationSeconds() : 5.0;
        MainWorkhorse.TimelineClip clip = new MainWorkhorse.TimelineClip(item, startTime, dur, trackIdx);
        tracks.get(trackIdx).getClips().add(clip);
        if (item != null) {
            item.addOnLoadCallback(() -> {
                if (item.getDurationSeconds() > 0) {
                    clip.setDurationSeconds(item.getDurationSeconds());
                }
                redraw();
            });
        }
        redraw();
    }

    private void selectClip(MainWorkhorse.TimelineClip clip) {
        this.selectedClip = clip;
        if (onClipSelected != null) onClipSelected.accept(clip);
        redraw();
    }

    public void deleteSelectedClip() {
        if (selectedClip != null) {
            tracks.get(selectedClip.getTrackIndex()).getClips().remove(selectedClip);
            selectClip(null);
            redraw();
        }
    }
}