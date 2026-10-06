# User Guide: Setup and Run

This document provides step-by-step instructions for installing prerequisites, compiling, and running **MMediatar** on Linux systems.

## System Prerequisites

MMediatar is designed and tested for desktop Linux environments running x86_64 architecture.

### 1. Java Development Kit (JDK) 11+
MMediatar targets Java 11 bytecode compatibility and utilizes the Java Platform Module System (JPMS).

```bash
# Verify installed Java version
java -version
javac -version
```

### 2. Apache Maven 3.8+
Maven manages build lifecycle, dependencies (`javafx-controls`, `javafx-fxml`, `javafx-media`), and native compilation options.

```bash
mvn -version
```

### 3. FFmpeg and FFprobe
FFmpeg is invoked as an external subprocess for audio waveform calculation, automatic media transcoding upon import, and the multi-pass timeline export pipeline.

```bash
ffmpeg -version
ffprobe -version
```

### 4. GStreamer 1.0 Native Libraries
JavaFX Media on Linux delegates decoding to GStreamer 1.0. If the base plugins are missing, media playback will fail with `MEDIA_UNAVAILABLE`.

#### Distribution Package Commands

=== "Debian / Ubuntu"
    ```bash
    sudo apt update
    sudo apt install -y openjdk-11-jdk maven ffmpeg \
        libgstreamer1.0-0 \
        gstreamer1.0-plugins-base \
        gstreamer1.0-plugins-good \
        gstreamer1.0-plugins-bad \
        gstreamer1.0-libav
    ```

=== "Fedora"
    ```bash
    sudo dnf install -y java-11-openjdk-devel maven ffmpeg \
        gstreamer1 \
        gstreamer1-plugins-base \
        gstreamer1-plugins-good \
        gstreamer1-plugins-bad-free \
        gstreamer1-libav
    ```

=== "Arch Linux"
    ```bash
    sudo pacman -S jdk11-openjdk maven ffmpeg \
        gstreamer \
        gst-plugins-base \
        gst-plugins-good \
        gst-plugins-bad \
        gst-libav
    ```

---

## Building and Running

### Method 1: Using the JavaFX Maven Plugin (Recommended)

The `javafx-maven-plugin` is pre-configured in `pom.xml` with the necessary native GStreamer library paths.

```bash
# Clone the repository
git clone https://github.com/niccher/Mediatar.git
cd Mediatar

# Compile and run
mvn clean compile
mvn javafx:run
```

The plugin automatically provides:
`-DGST_PLUGIN_PATH=/usr/lib/x86_64-linux-gnu/gstreamer-1.0` and `-Djava.library.path=/usr/lib/x86_64-linux-gnu/gstreamer-1.0`.

### Method 2: Running from Standalone JAR

To build a standalone JAR file:

```bash
mvn clean package
```

Then execute using your system's JavaFX runtime:

```bash
java --module-path /usr/share/openjfx/lib \
     --add-modules javafx.controls,javafx.fxml,javafx.media \
     -DGST_PLUGIN_PATH=/usr/lib/x86_64-linux-gnu/gstreamer-1.0 \
     -Djava.library.path=/usr/lib/x86_64-linux-gnu/gstreamer-1.0 \
     -cp target/Mediatar-1.0-SNAPSHOT.jar \
     com.nicch.mediatar.App
```

---

## Verifying the Installation

1. **Workspace Launch:** The application should display the 5-track timeline (`T1`, `V1`, `V2`, `A1`, `A2`), the media pool on the left, and transport controls at the bottom.
2. **Media Import:** Click `+ Import` and select an MP4, MOV, or MP3 file. The file should appear in the Media Pool list with metadata.
3. **Playback:** Drag the clip onto track `V1` or `A1` and press `Space`. The playhead moves smoothly and the 16-band VU meters illuminate.
4. **Export:** Click `Export Video` (or press `Ctrl+E`), select your desired resolution (e.g. 1080p), and click `Start Export`. Verify that the live FFmpeg console streams progress and completes with a success message.

For environment variables and tuning options, see [configuration.md](configuration.md).  
If you encounter runtime errors, consult [troubleshooting.md](troubleshooting.md).
