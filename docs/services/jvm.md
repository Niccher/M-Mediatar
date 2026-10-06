# Services: JVM and JavaFX Desktop Engine

This document provides software engineers with an in-depth reference for the Java 11 / JavaFX 22.0.1 desktop service powering **MMediatar**.

## Runtime and Module Configuration

MMediatar is packaged as a modular Java application adhering to the Java Platform Module System (JPMS).

### Module Declaration (`module-info.java`)

```java
module com.nicch.mediatar {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.xml;

    opens com.nicch.mediatar to javafx.fxml;
    exports com.nicch.mediatar;
}
```

- **`requires javafx.media;`**: Supplies the GStreamer native bridge, `MediaPlayer`, and `MediaView`.
- **`opens com.nicch.mediatar to javafx.fxml;`**: Enables JavaFX reflection to bind FXML fields into controllers (`MainWorkhorse`, `ExportDialogController`).
- **`requires java.xml;`**: Enables W3C DOM parsing and XML document transformation.

---

## Core Engine Architecture

```
src/main/java/com/nicch/mediatar/
├── App.java                     # Vanilla entry point
├── MMediatar.java               # JavaFX Application subclass
├── MainWorkhorse.java           # Central UI controller & playback loop
├── TimelinePane.java            # Custom Canvas-based multi-track timeline widget
├── Exporter.java                # Multi-pass FFmpeg rendering pipeline
├── ExportConfig.java            # Export parameters and callback interface
├── ExportDialogController.java  # FXML controller for export configuration
├── ProjectSerializer.java       # AES-128 encrypted XML persistence
└── ThemeManager.java            # Centralized CSS theme management
```

### 1. Dual-Stage Entry Point Pattern

To bypass standard JavaFX runtime classpath validation errors when launching without a modular module-path in certain IDEs:
- **`App.java`**: A plain Java class with `public static void main(String[] args)` that immediately delegates to `Application.launch(MMediatar.class, args)`.
- **`MMediatar.java`**: The actual `Application` instance initializing the primary `Stage`, reading `logo.png`, calculating initial visual bounds, and loading `mainworkhorse.fxml`.

### 2. Timeline Canvas Rendering Engine (`TimelinePane.java`)

Instead of creating thousands of discrete JavaFX node objects (which degrades performance during panning and scrubbing), `TimelinePane` extends `Region` and renders all tracks onto a single high-performance `Canvas`.

#### Coordinate Translation Math

All timeline layout transforms between **time in seconds** and **canvas pixels**:

$$\text{pixelX} = \frac{\text{timeSeconds}}{\text{zoomSecondsPerPixel}}$$

$$\text{timeSeconds} = \text{pixelX} \times \text{zoomSecondsPerPixel}$$

- **Track Height:** Each track is allocated fixed vertical space separated by divider lines.
- **Ruler:** A 30px top bar draws second divisions and formatted timecodes.
- **Magnetic Snapping:** If a dragging clip's leading or trailing edge falls within `SNAP_THRESHOLD = 0.2` seconds of an adjacent clip or the playhead, its position snaps automatically.

### 3. Playback Synchronization (`MainWorkhorse.java`)

Playback uses a high-resolution `AnimationTimer`:
- Every display frame (~16.6ms at 60Hz), the timer computes elapsed time:
  $$\Delta t = \frac{\text{now} - \text{lastUpdate}}{10^9}$$
- The global playhead advances: $\text{playhead} \mathrel{+}= \Delta t$.
- Active clips on tracks `V1`, `V2`, `A1`, and `A2` are queried. If the playhead enters a new clip, the corresponding `MediaPlayer` is created, seeked to the clip's offset, and started.
- A 16-band `AudioSpectrumListener` receives spectrum magnitudes and updates the 16 `ProgressBar` VU meters in the transport bar.

### 4. Multi-Pass FFmpeg Pipeline (`Exporter.java`)

When the user exports, `Exporter.java` runs an asynchronous pipeline:

1. **Phase 1 (V1 Master):** Gaps between clips on `V1` are identified. Black video segments are generated via `color=c=black:s=...:r=...:d=...` to ensure monotonic timestamps. Segments are joined into `v1_master.mp4`.
2. **Phase 2 (Audio Processing):** Audio clips on `A1`, `A2`, and `V2` are extracted and adjusted for speed using FFmpeg's `atempo` filter (clamped to `[0.5, 100.0]`).
3. **Phase 4 (Mixdown):** Audio masters are combined using FFmpeg's `amix` filtergraph (`amix=inputs=N:duration=longest:dropout_transition=3[aout]`).
4. **Phase 5 (Overlays):**
   - **Picture-in-Picture:** `[0:v][1:v]overlay=x=...:y=...`
   - **Text Overlays:** `drawtext=text='...':x=...:y=...:fontsize=...:fontcolor=...`
5. **Phase 6 (Cleanup):** Deletes temporary work files in `/tmp/mediatar_export*`.

---

## Engineer Recipes

### Adding a New Track Property
1. Add the field and getter/setter in `MainWorkhorse.TimelineClip`.
2. Update `ProjectSerializer.saveProject()` to serialize the XML tag and `loadProject()` to parse it.
3. Update `TimelinePane.redraw()` if the property alters visual rendering.
4. Update `Exporter.java` to incorporate the property into the FFmpeg filtergraph.

For local environment setup and IDE instructions, see [engineering/local-development.md](../engineering/local-development.md).
