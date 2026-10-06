# Architecture: Communication and Subprocesses

MMediatar operates without HTTP networking or client-server sockets. All communication takes place via:
1. **Thread-to-UI Event Dispatching:** Background threads update JavaFX UI elements via `Platform.runLater()`.
2. **Subprocess Pipes:** Standard input/output/error pipes connecting the JVM to `ffmpeg` and `ffprobe` processes.

---

## Communication Table

| Boundary | From | To | Transport | Format | Purpose |
|---|---|---|---|---|---|
| **Audio Downmixing** | `MainWorkhorse` | `ffmpeg` | Subprocess Pipe (`ProcessBuilder`) | Raw 8-bit mono PCM (`-f s8`) | Generating normalized audio waveform peaks |
| **Media Transcoding** | `MainWorkhorse` | `ffmpeg` | Subprocess Execution | CLI args + File I/O | Transcoding unsupported video/audio on import |
| **Stream Probing** | `MainWorkhorse` | `ffprobe` | Subprocess Pipe | JSON / CLI text | Detecting media duration, streams, and codecs |
| **Export Execution** | `Exporter` | `ffmpeg` | Multi-step Subprocess | Filtergraph CLI args | Track gap-filling, concat, audio mixing, overlays |
| **Log Streaming** | `ffmpeg stderr` | `ExportDialogController` | Java Thread `BufferedReader` | Plain text lines | Real-time console progress in UI dialog |
| **Playback Sync** | `AnimationTimer` | `MediaPlayer` | JavaFX Event Loop | Method calls (`seek()`, `play()`) | Multi-track playhead synchronization |

---

## Media Import and Waveform Extraction Sequence

When a user imports an audio or video file, MMediatar computes normalized amplitude peaks for waveform visualization:

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as MainWorkhorse (UI Thread)
    participant Thread as Background Worker
    participant FFmpeg as ffmpeg Subprocess
    participant Item as MediaItem Model

    User->>UI: Selects file via FileChooser
    UI->>Item: Instantiates MediaItem(file)
    UI->>Thread: Dispatches waveform extraction
    Thread->>FFmpeg: Executes ffmpeg -i file -ac 1 -ar 50 -f s8 -
    activate FFmpeg
    FFmpeg-->>Thread: Streams raw byte stream (50 Hz mono)
    deactivate FFmpeg
    Thread->>Item: Normalizes samples to [0.0, 1.0] float array
    Thread->>UI: Platform.runLater(timelinePane::redraw)
    UI-->>User: Waveform rendered across audio clip
```

---

## Multi-Pass Export Execution Sequence

The export pipeline (`Exporter.java`) executes asynchronously to prevent UI freeze:

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Dialog as ExportDialogController
    participant Exporter as Exporter Engine
    participant FS as Local Filesystem (/tmp)
    participant FFmpeg as ffmpeg Subprocess

    User->>Dialog: Clicks "Start Export"
    Dialog->>Exporter: exportTimeline(tracks, config)
    activate Exporter
    Exporter->>FS: Files.createTempDirectory("mediatar_export")
    
    rect rgb(240, 248, 255)
        note over Exporter,FFmpeg: Phase 1: V1 Video Track Processing
        Exporter->>FFmpeg: Render segments, fill gaps with black, concat to v1_master.mp4
        FFmpeg-->>FS: Writes v1_master.mp4
    end

    rect rgb(245, 255, 245)
        note over Exporter,FFmpeg: Phase 2: Audio Tracks (A1, A2, V2 audio)
        Exporter->>FFmpeg: Process audio clips, adjust atempo, render track_audio.wav
        FFmpeg-->>FS: Writes track audio masters
    end

    rect rgb(255, 250, 240)
        note over Exporter,FFmpeg: Phase 4: Audio Mixdown & Video Pairing
        Exporter->>FFmpeg: ffmpeg -i v1_master -i a1 -i a2 -filter_complex amix ...
        FFmpeg-->>FS: Writes mixed_pre_overlay.mp4
    end

    rect rgb(255, 240, 245)
        note over Exporter,FFmpeg: Phase 5: Overlays (V2 PiP & T1 Text)
        Exporter->>FFmpeg: ffmpeg -filter_complex "[0:v][1:v]overlay=...,drawtext=..."
        FFmpeg-->>FS: Writes final destination file
    end

    Exporter->>FS: Clean up temporary files in /tmp
    Exporter->>Dialog: Callback: onComplete(true, destinationFile)
    deactivate Exporter
    Dialog-->>User: Displays completion notification
```

---

## Thread Safety and Stream Consumption

To prevent deadlocks when executing native subprocesses in Java:
- `ProcessBuilder` merges standard error with standard output (`redirectErrorStream(true)`) or consumes standard error concurrently using a dedicated `BufferedReader`.
- Stream consumption lines are parsed and forwarded to JavaFX UI controls strictly wrapped inside `Platform.runLater(() -> callback.onLog(line))`.
- Cancellation safely signals `process.destroyForcibly()` to terminate any orphaned transcoding subprocesses.

For data serialization and file formats, see [data-and-storage.md](data-and-storage.md).
