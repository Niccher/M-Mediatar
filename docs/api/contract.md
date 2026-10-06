# Internal API and Data Contracts

While **MMediatar** is an offline desktop application without an external HTTP REST interface, its internal components communicate across strictly defined Java DTOs, XML persistence contracts, and FFmpeg filtergraph protocols.

---

## 1. Export Configuration Contract (`ExportConfig.java`)

The `ExportConfig` object encapsulates all rendering parameters passed from the UI dialog to the background export worker.

### Fields and Types

| Field | Type | Required | Constraints / Valid Values | Description |
|---|---|---|---|---|
| `destinationFile` | `java.io.File` | Yes | Non-null, writable target directory | Final rendered output file path. |
| `resolution` | `String` | Yes | `"1920x1080"`, `"1280x720"`, etc. | Output dimensions in `WIDTHxHEIGHT` format. |
| `fps` | `int` | Yes | `[24, 120]` | Framerate per second. |
| `videoBitrate` | `String` | Yes | `"500k"` to `"50000k"` | Target video bitrate passed to FFmpeg `-b:v`. |
| `videoCodec` | `String` | Yes | `"libx264"`, `"libx265"`, `"vp9"`, `"copy"` | Video encoder passed to `-c:v`. |
| `audioCodec` | `String` | Yes | `"aac"`, `"mp3"`, `"flac"`, `"copy"` | Audio encoder passed to `-c:a`. |
| `audioBitrate` | `String` | Yes | `"64k"` to `"320k"` | Target audio bitrate passed to `-b:a`. |
| `format` | `String` | Yes | `".mp4"`, `".mkv"`, `".webm"`, `".mp3"`, etc. | Output container file extension. |
| `callback` | `LogCallback` | Yes | Instance of `ExportConfig.LogCallback` | Interface receiving progress logs and completion state. |

### Callback Interface Definition

```java
public interface LogCallback {
    void onLog(String message);
    void onProgress(double progress);
    void onComplete(boolean success, File outputFile);
}
```

---

## 2. Timeline Clip Data Contract (`TimelineClip`)

The `TimelineClip` inner class in `MainWorkhorse.java` defines the state of a single edit segment on the timeline.

| Property | Type | Default | Description |
|---|---|---|---|
| `mediaItem` | `MediaItem` | `null` (text) | Underlying source file and waveform cache. |
| `startSeconds` | `double` | `0.0` | Position of clip in seconds relative to timeline origin. |
| `durationSeconds` | `double` | `5.0` | Active timeline duration of the clip. |
| `mediaStartOffset`| `double` | `0.0` | In-point offset into source media file. |
| `playbackSpeed` | `double` | `1.0` | Playback speed multiplier (`[0.25, 4.0]`). |
| `opacity` | `double` | `1.0` | Visual transparency (`[0.0, 1.0]`). |
| `scale` | `double` | `1.0` | PiP scaling factor for track `V2` (`[0.1, 2.0]`). |
| `posX` | `double` | `0.0` | X-axis coordinate offset in pixels. |
| `posY` | `double` | `0.0` | Y-axis coordinate offset in pixels. |
| `isMuted` | `boolean` | `false` | Audio mute toggle flag. |
| `aliasName` | `String` | `null` | Optional user-defined display name override. |
| `textContent` | `String` | `""` | Overlay text string (track `T1` only). |
| `textColor` | `String` | `"#ffffff"` | Hex color string for overlay text. |
| `fontSize` | `double` | `32.0` | Font size in points for text overlay. |

---

## 3. Project XML File Contract

When decrypted, the `.mediatar` XML document adheres to the following element hierarchy:

```
<project version="1.0">
  ├── <mediaPool>
  │     └── <file> [string: absolute path] </file> (0..*)
  └── <tracks>
        └── <track name="[T1|V1|V2|A1|A2]" type="[TEXT|VIDEO|AUDIO]"> (5)
              └── <clip> (0..*)
                    ├── <filePath> [string: path] </filePath> (0..1)
                    ├── <start> [double] </start>
                    ├── <duration> [double] </duration>
                    ├── <offset> [double] </offset>
                    ├── <speed> [double] </speed>
                    ├── <opacity> [double] </opacity>
                    ├── <scale> [double] </scale>
                    ├── <posX> [double] </posX>
                    ├── <posY> [double] </posY>
                    ├── <isMuted> [boolean] </isMuted>
                    ├── <aliasName> [string] </aliasName> (0..1)
                    ├── <textContent> [string] </textContent> (TEXT only)
                    ├── <textColor> [string: hex] </textColor> (TEXT only)
                    └── <fontSize> [double] </fontSize> (TEXT only)
```

---

## 4. Subprocess Execution Protocol

All invocations of `ffmpeg` adhere to the standard exit code convention:
- **Exit Code 0:** Execution succeeded; intermediate or target file verified on filesystem.
- **Exit Code Non-Zero:** Process terminated with an error; standard error contents captured and routed to `LogCallback.onLog()`.

For details on the JavaFX service architecture, see [services/jvm.md](../services/jvm.md).
