# User Guide: Configuration

This document outlines the runtime parameters, environment variables, UI options, and export configurations available in **MMediatar**.

## Runtime Options and JVM Flags

When launching MMediatar directly via `java` or through Maven, the following system properties configure media hardware interop and rendering behavior:

| Property | Default Value | Description |
|---|---|---|
| `-DGST_PLUGIN_PATH` | `/usr/lib/x86_64-linux-gnu/gstreamer-1.0` | Informs JavaFX Media where native GStreamer decoder plugins reside. |
| `-Djava.library.path` | `/usr/lib/x86_64-linux-gnu/gstreamer-1.0` | Native shared library path for GStreamer bindings (`libgstreamer-1.0.so`). |
| `-Dprism.order` | `es2,sw` | Controls JavaFX graphics pipeline fallback (OpenGL ES 2 hardware acceleration, falling back to software). |
| `-Dglass.gtk.uiScale` | `1.0` | Overrides GTK display scaling factor (useful for HiDPI or fractional scaling under Wayland). |

### Customizing Paths by Distribution

If your Linux distribution stores GStreamer libraries in non-standard paths, pass them during launch:

=== "Fedora / RHEL"
    ```bash
    mvn javafx:run -Doptions="-DGST_PLUGIN_PATH=/usr/lib64/gstreamer-1.0 -Djava.library.path=/usr/lib64/gstreamer-1.0"
    ```

=== "Arch Linux"
    ```bash
    mvn javafx:run -Doptions="-DGST_PLUGIN_PATH=/usr/lib/gstreamer-1.0 -Djava.library.path=/usr/lib/gstreamer-1.0"
    ```

---

## User Interface Themes

MMediatar includes three built-in CSS themes managed by `ThemeManager`:

| Theme | Base Palette | Background | Purpose |
|---|---|---|---|
| **Solarized (Default)** | `#eee8d5` | `#fdf6e3` | Low-contrast warm palette designed for long editing sessions. |
| **Dark** | `#2d2d2d` | `#1e1e1e` | High-contrast dark mode for low-light editing environments. |
| **Light** | `#ffffff` | `#f4f4f4` | High-brightness clean aesthetic. |

Themes can be changed at runtime via the top menu bar (`View → Themes`) without restarting the workspace.

---

## Export Preset Configuration

The export dialog provides fine-grained control over video rendering:

| Setting | Presets / Options | Default |
|---|---|---|
| **Resolution** | 8K (7680×4320), 4K (3840×2160), 2K (2560×1440), **1080p (1920×1080)**, 720p (1280×720), 480p (854×480), 360p (640×360), 240p (426×240) | 1080p |
| **Framerate** | 24, 25, **30**, 50, 60, 120 fps | 30 fps |
| **Video Bitrate** | 500k, 1000k, 2000k, 3000k, **5000k**, 8000k, 10000k, 15000k, 20000k, 50000k | 5000k |
| **Video Codec** | **libx264** (H.264), libx265 (HEVC), vp9, mpeg4, copy | libx264 |
| **Audio Codec** | **aac**, mp3, flac, libvorbis, opus, pcm_s16le, copy | aac |
| **Audio Bitrate** | 64k, 96k, 128k, **192k**, 256k, 320k | 192k |
| **Container Format** | **.mp4**, .mkv, .webm, .avi, .mov, .3gp, .mp3, .aac, .m4a, .flac, .ogg | .mp4 |

> **Note on Audio-Only Export:** If the timeline contains only audio clips (tracks `A1` and `A2`), the export dialog automatically selects audio container defaults (`.mp3` or `.m4a`) and bypasses video rendering passes.

For step-by-step setup, see [setup-and-run.md](setup-and-run.md).  
For troubleshooting runtime issues, see [troubleshooting.md](troubleshooting.md).
