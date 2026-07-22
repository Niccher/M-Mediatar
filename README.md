<div align="center">
  <h1>MMediatar</h1>
  <p><em>Lightweight multi-track video editor for Linux — timeline editing, real-time preview, and ffmpeg-based export</em></p>

  <a href="#"><img src="https://img.shields.io/badge/Java-11-e11?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 11"/></a>
  <a href="#"><img src="https://img.shields.io/badge/JavaFX-22.0.1-007396?style=for-the-badge&logo=java&logoColor=white" alt="JavaFX 22.0.1"/></a>
  <a href="#"><img src="https://img.shields.io/badge/Maven-3.8+-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Maven"/></a>
  <a href="#"><img src="https://img.shields.io/badge/FFmpeg-required-007808?style=for-the-badge&logo=ffmpeg&logoColor=white" alt="FFmpeg required"/></a>
  <a href="#"><img src="https://img.shields.io/badge/license-MIT-9cf?style=for-the-badge" alt="License"/></a>
</div>

---

## About the Project

MMediatar is a desktop video editor built with JavaFX. It provides a multi-track timeline (text, two video, two audio tracks), a media pool for importing clips, real-time preview via JavaFX `MediaPlayer`, and a full export pipeline driven by native ffmpeg. The target user is anyone who needs a straightforward, non-linear video editor on Linux without the overhead of a full professional suite.

The application is a standalone single-repo project — there are no sibling services, backends, or external APIs.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    MMediatar (JavaFX Desktop)                │
│                                                              │
│  ┌─────────────┐   ┌──────────────────┐   ┌──────────────┐  │
│  │  Media Pool  │   │  Timeline (5 trk) │   │  Inspector    │  │
│  │  (ListView)  │──▶│  Canvas-based     │──▶│  Properties   │  │
│  └─────────────┘   │  TimelinePane     │   │  Panel        │  │
│                    └────────┬─────────┘   └──────────────┘  │
│                             │                                │
│                    ┌────────▼─────────┐                      │
│                    │  Preview          │                      │
│                    │  MediaView x2     │                      │
│                    │  (V1 + V2 PiP)    │                      │
│                    └────────┬─────────┘                      │
│                             │                                │
│                    ┌────────▼─────────┐                      │
│                    │  Export Dialog    │                      │
│                    │  (ffmpeg config)  │                      │
│                    └────────┬─────────┘                      │
└─────────────────────────────┼────────────────────────────────┘
                              │ ffmpeg subprocess
                    ┌─────────▼──────────┐
                    │  ffmpeg (native)    │
                    │  Rendering Pipeline │
                    └────────────────────┘
```

**Dependency flow:**
- The user imports media files through the **Media Pool**. Files are optionally transcoded to JavaFX-compatible formats (MP4/H.264 for video, MP3 for audio) via ffmpeg on import.
- Clips are dragged from the pool onto one of the five **Timeline** tracks (T1=Text, V1=Video, V2=Video PiP, A1/A2=Audio).
- **TimelinePane** handles all interaction (select, move, trim, split, snap, drag-and-drop) using a custom `Canvas` widget.
- The **Preview** area uses two stacked `MediaView` components (V1 main, V2 picture-in-picture) with real-time audio spectrum VU meters.
- The **Inspector** panel exposes per-clip properties: speed, opacity, scale, position X/Y, and text content for title clips.
- The **Export Dialog** (`ExportDialogController`) lets the user configure resolution, framerate, bitrate, codecs, and format. On render, `Exporter` builds ffmpeg subprocesses that:
  1. Process each video/audio track independently (gap-filling with black or thumbnail stills).
  2. Concatenate per-track segments.
  3. Mix all audio inputs via ffmpeg's `amix` filter.
  4. Apply overlays: V2 PiP (scaled/positioned overlay) and text (ffmpeg `drawtext` filter).
  5. Write the final output file.
- **Project Serialization** saves/loads the full workspace to AES-encrypted XML (`.mediatar` files) using AES/ECB/PKCS5Padding with a static 128-bit key.

## Machine Learning / Algorithms

This codebase does **not** use machine learning, statistical classification, or any trained models. All algorithmic components are deterministic signal-processing utilities:

| Component | Method | Mechanism |
|---|---|---|
| Audio waveform peaks | Normalized amplitude binning | Downmixes audio to mono at 50 Hz via `ffmpeg -ac 1 -ar 50 -f s8 -`, normalizes each sample to `[0, 1]` |
| VU meter | JavaFX `AudioSpectrumListener` (16 bands) | Magnitudes mapped to `ProgressBar`; color thresholds: green (<65%), yellow (65–85%), red (>85%) |
| Timeline snap | Distance-threshold magnetism | If clip boundary or playhead is within 0.2 seconds of another boundary, snaps to it |
| Atempo clamping | Arithmetic clamp | Speed clamped to `[0.5, 100.0]` to satisfy ffmpeg `atempo` bounds |

## Features

### Editing & Timeline

| Feature | Description |
|---|---|
| **5-track timeline** | T1 (Text), V1 (Video), V2 (Video PiP), A1/A2 (Audio) |
| **Selection tool** | Click to select, drag to move (cross-track), edge-drag to trim |
| **Blade tool** | Click anywhere on a clip to split it into two segments |
| **Snapping** | Magnetic snap to playhead and clip boundaries (toggle via toolbar) |
| **Right-click context menu** | Play/pause, detach audio, toggle mute, trim 1s from both ends, merge with next, delete |
| **Keyboard shortcuts** | `V` select, `C` blade, `Space` play/pause, `Delete`/`Backspace` remove clip |
| **Inspector panel** | Per-clip speed (0.25x–4.0x), opacity, scale, position X/Y; text content/color/size for title clips |

### Media Import

| Feature | Description |
|---|---|
| **Import dialog** | Opens native file chooser; filters by common video/audio extensions |
| **Auto-transcoding** | Unsupported formats (AAC→M4A, WebM/MKV/MOV→MP4, OGG/FLAC→MP3) transparently converted via ffmpeg on import |
| **Audio waveform** | Per-clip waveform visualization drawn on audio clips in the timeline |
| **Media pool** | Sorted list with file metadata tooltip; drag-and-drop to timeline; "Show in File Manager" context action |

### Playback & Preview

| Feature | Description |
|---|---|
| **V1 + V2 PiP preview** | Two stacked `MediaView` elements for main video and picture-in-picture |
| **16-band VU meter** | Real-time audio spectrum visualization with color-coded levels |
| **Timeline playback** | Playhead advances linearly; clips on all tracks are evaluated and seeked synchronously |
| **Transport controls** | Play/pause, step back/forward (0.1s), rewind/fast-forward (5s), loop toggle |
| **Large timecode display** | HH:MM:SS.FF format at 30 fps |

### Export

| Feature | Description |
|---|---|
| **Resolution presets** | 8K to 240p (7680×4320 down to 426×240) |
| **Framerate** | 24, 25, 30, 50, 60, 120 fps |
| **Video codecs** | libx264, libx265, vp9, mpeg4, copy |
| **Audio codecs** | aac, mp3, flac, libvorbis, opus, pcm_s16le, copy |
| **Container formats** | MP4, MKV, WebM, AVI, MOV, 3GP, MP3, AAC, M4A, FLAC, OGG |
| **Smart defaults** | Auto-selects audio-only format if timeline has no video clips |
| **Live ffmpeg console** | Real-time ffmpeg output in the export dialog |

### Themes

| Theme | Base color | Background |
|---|---|---|
| Solarized (default) | `#eee8d5` | `#fdf6e3` |
| Dark | `#2d2d2d` | `#1e1e1e` |
| Light | `#ffffff` | `#f4f4f4` |

### Project Management

| Feature | Description |
|---|---|
| **Save/Load** | AES-encrypted XML (`.mediatar` files) preserving media pool, tracks, clip properties, and text overlays |
| **Clear timeline** | Removes all clips while preserving media pool |

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java (OpenJDK) | 11 |
| UI Framework | JavaFX | 22.0.1 |
| UI Module | javafx-controls | 22.0.1 |
| FXML | javafx-fxml | 22.0.1 |
| Media | javafx-media | 22.0.1 |
| Build | Maven | 3.8+ (plugin 0.0.8) |
| XML / Encryption | java.xml + javax.crypto | JDK 11 bundled |
| Video Processing | ffmpeg (external) | *(system-installed)* |
| Media Analysis | ffprobe (external) | *(system-installed)* |

## Prerequisites

- **JDK 11** or later (the project targets Java 11 release)
- **Maven 3.8+**
- **ffmpeg** (must be on `PATH`) — used for all media transcoding and export rendering
- **ffprobe** (optional, recommended) — used for media stream detection
- **GStreamer** native libraries for JavaFX media playback on Linux (`/usr/lib/x86_64-linux-gnu/gstreamer-1.0`)

## Installation & Setup

### Clone and build

```bash
git clone https://github.com/your-org/Mediatar.git
cd Mediatar
mvn clean compile
```

### Run

```bash
mvn javafx:run -DGST_PLUGIN_PATH=/usr/lib/x86_64-linux-gnu/gstreamer-1.0 \
               -Djava.library.path=/usr/lib/x86_64-linux-gnu/gstreamer-1.0
```

Or package and run the JAR directly:

```bash
mvn package
java --module-path /path/to/javafx-sdk-22.0.1/lib \
     --add-modules javafx.controls,javafx.fxml,javafx.media \
     -DGST_PLUGIN_PATH=/usr/lib/x86_64-linux-gnu/gstreamer-1.0 \
     -Djava.library.path=/usr/lib/x86_64-linux-gnu/gstreamer-1.0 \
     -cp target/Mediatar-1.0-SNAPSHOT.jar \
     com.nicch.mediatar.App
```

> GStreamer library path may differ by distribution. The paths above are for Debian/Ubuntu on x86_64.

### Installing ffmpeg

```bash
# Debian / Ubuntu
sudo apt install ffmpeg

# Fedora
sudo dnf install ffmpeg

# Arch
sudo pacman -S ffmpeg
```

## Project Structure

```
Mediatar/
├── pom.xml                              # Maven build (Java 11, JavaFX 22.0.1)
├── TestMedia.java                       # Standalone JavaFX Media loader test
├── nbactions.xml                        # NetBeans launcher config
├── src/
│   └── main/
│       ├── java/
│       │   ├── module-info.java         # Java module declaration
│       │   └── com/nicch/mediatar/
│       │       ├── App.java             # Vanilla entry point
│       │       ├── MMediatar.java       # JavaFX Application launcher
│       │       ├── MainWorkhorse.java   # Main controller (1644 lines)
│       │       ├── TimelinePane.java    # Custom Canvas timeline widget
│       │       ├── ExportConfig.java    # Export parameter DTO
│       │       ├── ExportDialogController.java  # Export dialog logic
│       │       ├── Exporter.java        # ffmpeg rendering pipeline
│       │       ├── ProjectSerializer.java  # AES XML save/load
│       │       └── ThemeManager.java    # Solarized/Dark/Light themes
│       └── resources/
│           └── com/nicch/mediatar/
│               ├── mainworkhorse.fxml   # Main window layout
│               ├── export_dialog.fxml   # Export dialog layout
│               ├── audio_thumbnail.png  # Placeholder for audio tracks
│               ├── logo.png             # App icon (PNG)
│               └── logo.svg             # App icon (SVG)
```

## Usage / API Reference

This is a desktop application with no HTTP API. All interaction is through the JavaFX GUI. Key FXML-triggered controller methods:

| Method | Trigger | Description |
|---|---|---|
| `handleImportAction()` | File → Open Media File / +Import | Opens native file chooser, imports and optionally transcodes media |
| `togglePlayPause()` | Space key / Play button | Toggles preview playback |
| `playTimeline()` | Play Timeline button | Starts timeline playback from current playhead |
| `stopTimeline()` | Stop button | Stops timeline, resets playhead to 0 |
| `handleAddTextAction()` | T Add Text button | Adds a 5-second text clip to the T1 track |
| `handleExportAction()` | File → Export Video / Export button | Opens the export dialog |
| `handleSaveAction()` | File → Save Project | Saves project to AES-encrypted `.mediatar` file |
| `handleLoadAction()` | File → Open Project | Loads `.mediatar` project file |
| `resetClipProperties()` | Reset Properties button | Resets speed/opacity/scale/position to defaults |

### Export settings

| Control | Options (default first) |
|---|---|
| Resolution | 7680×4320, 3840×2160, 2560×1440, **1920×1080**, 1280×720, 854×480, 640×360, 426×240 |
| Framerate | 24, 25, **30**, 50, 60, 120 |
| Video Bitrate | 500k, 1000k, 2000k, 3000k, **5000k**, 8000k, 10000k, 15000k, 20000k, 50000k |
| Video Codec | **libx264**, libx265, vp9, mpeg4, copy |
| Audio Codec | **aac**, mp3, flac, libvorbis, opus, pcm_s16le, copy |
| Audio Bitrate | 64k, 96k, 128k, **192k**, 256k, 320k |
| Format | **.mp4**, .mkv, .webm, .avi, .mov, .3gp, .mp3, .aac, .m4a, .flac, .ogg |

## License

No license file is included in this repository. All rights reserved by the author unless otherwise specified.

## Contributing

1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/my-feature`).
3. Commit your changes (`git commit -am 'Add my feature'`).
4. Push to the branch (`git push origin feature/my-feature`).
5. Open a Pull Request.

---

<div align="center"><sub>Built with JavaFX 22.0.1 · Maven · ffmpeg</sub></div>
