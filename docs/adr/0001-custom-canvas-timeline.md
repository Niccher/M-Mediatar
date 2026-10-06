# ADR 0001: Custom Canvas-Based Timeline Rendering

- **Status:** Accepted
- **Deciders:** MMediatar Architecture Team
- **Date:** 2026-09-15

## Context and Problem Statement

A non-linear video editor requires rendering a multi-track timeline containing potentially hundreds of video, audio, and text clips. Each clip requires rendering selection outlines, trim handles, timecode labels, and audio waveform amplitude peaks. Scrubbing the playhead requires redrawing at 60 frames per second.

Should we construct the timeline using JavaFX Scene Graph UI nodes (e.g. `HBox`, `Pane`, `Rectangle`, `Label`) or render directly onto an immediate-mode `Canvas`?

## Decision Drivers

- **Rendering Performance:** Scrubbing and panning must maintain 60 FPS without garbage collection pauses.
- **Memory Overhead:** Managing thousands of scene graph node objects exhausts JVM heap during long editing sessions.
- **Hit Testing & Interaction:** Precise sub-pixel blade cutting, magnetic snapping, and edge trimming require custom coordinate calculations.

## Considered Options

1. **Option 1: Nested JavaFX Scene Graph Nodes:** Hierarchical nodes (`VBox` for tracks, `Pane` for track lanes, nested `Rectangle` and `Text` nodes for clips).
2. **Option 2: Immediate-Mode `javafx.scene.canvas.Canvas`:** A single lightweight `Canvas` widget where tracks, clips, playhead, and waveforms are drawn using `GraphicsContext`.

## Decision Outcome

Chosen **Option 2: Immediate-Mode `Canvas`** (implemented in `TimelinePane.java`).

### Consequences

#### Positive
- **High Performance:** Renders hundreds of clips in a single double-buffered pass with negligible GC churn.
- **Total Drawing Control:** Waveforms, snapping magnetism lines, playhead scrubbers, and selection borders can be drawn with custom anti-aliasing and pixel-perfect coordinate mapping.
- **Dynamic Zoom:** Timeline zooming is a straightforward scalar multiplication without resizing hundreds of layout nodes.

#### Negative
- **Manual Hit Testing:** Tooltips, drag-and-drop, and cursor state changes must be hand-calculated from mouse coordinates (`x`, `y`) rather than leveraging JavaFX node event dispatching.
- **No Automatic Accessibility:** Canvas rendering does not expose individual clip nodes to OS screen readers by default.

For rendering pipeline decisions, see [0002-native-ffmpeg-subprocess-rendering.md](0002-native-ffmpeg-subprocess-rendering.md).
