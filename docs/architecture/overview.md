# Architecture Overview

MMediatar is an offline, standalone desktop multi-track video editor built on the Java Virtual Machine using JavaFX 22.0.1 and native FFmpeg subprocesses.

## System Context (C4 Context)

```mermaid
flowchart TB
    User([Desktop User / Editor])
    
    subgraph HostSystem [Linux Desktop Host]
        MMediatar[MMediatar Application\nJava 11 / JavaFX 22]
        FFmpegProc[FFmpeg & FFprobe\nCLI Subprocesses]
        GStreamerLib[GStreamer 1.0\nNative Shared Libraries]
        LocalFS[(Local File System\nVideos, Audio, Projects)]
        DisplayAudio[Display & Audio Subsystems\nWayland / X11 & PulseAudio / PipeWire]
    end

    User -->|Keyboard, Mouse & File Chooser| MMediatar
    MMediatar -->|CLI Subprocess Execution| FFmpegProc
    MMediatar -->|JNI / JNA Media Playback| GStreamerLib
    MMediatar -->|Read / Write .mediatar & Media| LocalFS
    FFmpegProc -->|Read Sources & Render Output| LocalFS
    GStreamerLib -->|Render Video & Sound| DisplayAudio
    MMediatar -->|Present GUI & VU Bars| DisplayAudio
```

---

## Container & Component Architecture (C4 Container)

Within the JVM process, MMediatar is partitioned into decoupled subsystems:

```mermaid
flowchart TB
    subgraph MMediatarJVM [JVM Process: MMediatar]
        Bootstrap[Bootstrap & Lifecycle\nApp.java / MMediatar.java]
        
        subgraph UI [User Interface Layer]
            FXML[FXML Layouts\nmainworkhorse.fxml / export_dialog.fxml]
            Workhorse[Controller: MainWorkhorse.java\nEvent Routing, State & Transport]
            Timeline[Canvas Widget: TimelinePane.java\nMulti-track rendering, scrubbing, blade, snap]
            Theme[ThemeManager.java\nSolarized / Dark / Light CSS]
        end

        subgraph CoreEngine [Core Desktop Engine]
            Preview[Preview Subsystem\nMediaView x2 V1 & V2 PiP, 16-band VU]
            Serializer[ProjectSerializer.java\nAES-128 Encrypted XML Persistence]
            ExportBridge[Exporter.java & ExportConfig.java\nFiltergraph construction & subprocess runner]
        end
    end

    subgraph NativeOS [Native Linux OS]
        FFMPEG[ffmpeg / ffprobe\nExternal Subprocesses]
        GST[libgstreamer-1.0.so\nDecoding & Playback]
        DISK[(Disk Storage\n.mediatar XML & Rendered Video)]
    end

    Bootstrap --> UI
    UI --> CoreEngine
    Workhorse --> Timeline
    Workhorse --> Preview
    Workhorse --> Serializer
    Workhorse --> ExportBridge
    
    Preview -->|Decoder binding| GST
    Serializer -->|AES-128 read/write| DISK
    ExportBridge -->|ProcessBuilder CLI invocation| FFMPEG
    FFMPEG -->|Temporary clips & final encode| DISK
```

---

## Subsystem Breakdown

| Subsystem | Primary Source Files | Runtime Role | Communication Mechanism |
|---|---|---|---|
| **Bootstrap & Lifecycle** | `App.java`, `MMediatar.java` | Initializes JavaFX runtime, loads icons, sets up responsive primary Stage | In-process JavaFX `Application.launch()` |
| **Main Workhorse Controller** | `MainWorkhorse.java` | Manages track data models, playhead animation timer, audio sync, transport controls, inspector panel | JavaFX property bindings & event handlers |
| **Timeline Canvas** | `TimelinePane.java` | Custom `Canvas` widget drawing 5 tracks (T1, V1, V2, A1, A2), clips, selection borders, playhead, snapping lines, waveforms | Direct double-buffered 2D canvas drawing |
| **Preview & VU Meter** | `MainWorkhorse.java`, `TimelinePane.java` | Two stacked `MediaView` elements (V1 primary, V2 picture-in-picture) and 16-band `AudioSpectrumListener` | JavaFX `MediaPlayer` callbacks |
| **Rendering Pipeline** | `Exporter.java`, `ExportConfig.java` | Translates timeline track state into complex FFmpeg command lines (gap filling, concat, amix, overlay, drawtext) | Standard I/O stream redirection via `ProcessBuilder` |
| **Project Persistence** | `ProjectSerializer.java` | Serializes tracks, clips, speeds, and overlays into AES-128 encrypted XML files (`.mediatar`) | In-memory DOM parser (`javax.xml`) and `Cipher` |
| **Theming** | `ThemeManager.java` | Controls unified color palette across workspace and dialogs | Dynamic JavaFX inline CSS injection |

---

## Process Model and Trust Boundaries

- **Single User Desktop Application:** MMediatar runs entirely within the user's desktop session. There are no remote daemon processes, server ports, or multi-tenant boundaries.
- **Process Isolation:** Heavy media rendering and transcoding are isolated to external `ffmpeg` subprocesses. If FFmpeg crashes due to a corrupt codec stream, the JVM UI remains responsive and captures the standard error log.
- **File System Permissions:** The application operates strictly with the privileges of the running Linux user account, accessing media files and project storage locally.

For deep dive into subprocess interaction, see [communication.md](communication.md).  
For the persistence schema and encryption details, see [data-and-storage.md](data-and-storage.md).
