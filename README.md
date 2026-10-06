# MMediatar

A lightweight, multi-track non-linear desktop video editor for Linux built with JavaFX and FFmpeg. Features 5-track timeline editing (text, 2 video, 2 audio), real-time playback preview, waveform visualization, and a multi-pass FFmpeg export engine.

Stack: Java 11 (OpenJDK), JavaFX 22.0.1, Maven, FFmpeg (native).

**If you only need to run the application, this page is enough.**  
Software engineers: see [docs/README.md](docs/README.md).

## What “running” looks like

| Interface Component | How to Open / View | Description |
|---|---|---|
| Main Workspace | Desktop window launches on startup | 5-track canvas timeline, media pool, playback controls |
| Video Preview | Centered preview pane | Real-time JavaFX MediaView with V1 main and V2 PiP |
| Live VU Meter | Bottom transport bar | 16-band audio spectrum levels during playback |
| Export Dialog | File → Export Video or `Ctrl+E` | Configurable resolution, framerate, codecs, and container formats |

## Prerequisites

- **Operating System:** Linux (x86_64 Debian/Ubuntu, Fedora, Arch)
- **Java Development Kit:** OpenJDK 11 or later
- **Build Tool:** Apache Maven 3.8+
- **Media Engine:** `ffmpeg` and `ffprobe` installed on system `PATH`
- **Native Runtime:** GStreamer 1.0 libraries (for JavaFX Media playback on Linux)

```bash
# Debian / Ubuntu
sudo apt update && sudo apt install -y openjdk-11-jdk maven ffmpeg libgstreamer1.0-0 gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-libav

# Fedora
sudo dnf install -y java-11-openjdk-devel maven ffmpeg gstreamer1 gstreamer1-plugins-base gstreamer1-plugins-good gstreamer1-plugins-bad-free

# Arch Linux
sudo pacman -S jdk11-openjdk maven ffmpeg gstreamer gst-plugins-base gst-plugins-good gst-plugins-bad
```

## Setup and Run

Numbered, copy-pasteable steps to launch from terminal:

1. Clone repository:
   ```bash
   git clone https://github.com/niccher/Mediatar.git
   cd Mediatar
   ```
2. Build the project:
   ```bash
   mvn clean compile
   ```
3. Launch with JavaFX plugin (recommended):
   ```bash
   mvn javafx:run
   ```
4. Or package and execute the standalone JAR:
   ```bash
   mvn package
   java --module-path /usr/share/openjfx/lib \
        --add-modules javafx.controls,javafx.fxml,javafx.media \
        -DGST_PLUGIN_PATH=/usr/lib/x86_64-linux-gnu/gstreamer-1.0 \
        -Djava.library.path=/usr/lib/x86_64-linux-gnu/gstreamer-1.0 \
        -cp target/Mediatar-1.0-SNAPSHOT.jar \
        com.nicch.mediatar.App
   ```

## Configuration Users May Change

| Parameter | Configuration Method | Default | Purpose |
|---|---|---|---|
| `GST_PLUGIN_PATH` | JVM option / environment | `/usr/lib/x86_64-linux-gnu/gstreamer-1.0` | Path to GStreamer 1.0 plugins |
| `java.library.path` | JVM option | `/usr/lib/x86_64-linux-gnu/gstreamer-1.0` | Path to native GStreamer libraries |
| Theme | View → Themes menu | Solarized | UI color theme (Solarized, Dark, Light) |
| Export Destination | Export Dialog file chooser | `output.mp4` in user home | Rendered video output path |

Full configuration reference: [docs/user/configuration.md](docs/user/configuration.md).

## Something Went Wrong?

| Symptom | Cause | Solution |
|---|---|---|
| `MediaException: MEDIA_UNAVAILABLE` | GStreamer native libraries not found | Ensure GStreamer packages are installed and check `GST_PLUGIN_PATH` |
| `Cannot run program "ffmpeg"` | `ffmpeg` not in system `PATH` | Install FFmpeg via package manager (`sudo apt install ffmpeg`) |
| Silent audio playback | System audio backend muted or wrong device | Uncheck Mute toggle and check system PulseAudio/PipeWire volume |
| UI scaling blur on Wayland | Fractional display scaling | Launch with `-Dglass.gtk.uiScale=1.0` or under XWayland |

Detailed resolution steps: [docs/user/troubleshooting.md](docs/user/troubleshooting.md).

## Software Engineers

- **Engineering Index:** [docs/README.md](docs/README.md)
- **Architecture Overview:** [docs/architecture/overview.md](docs/architecture/overview.md)
- **Subprocess Communication:** [docs/architecture/communication.md](docs/architecture/communication.md)
- **How to Make Changes:** [docs/engineering/making-changes.md](docs/engineering/making-changes.md)
