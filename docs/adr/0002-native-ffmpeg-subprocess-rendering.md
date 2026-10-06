# ADR 0002: Native FFmpeg Subprocesses for Export and Transcoding

- **Status:** Accepted
- **Deciders:** MMediatar Architecture Team
- **Date:** 2026-09-15

## Context and Problem Statement

MMediatar requires multi-pass video transcoding, gap-filling with black video, picture-in-picture scaling, audio mixing with `amix`, text overlay via `drawtext`, and final encoding across diverse container formats (`.mp4`, `.mkv`, `.webm`, `.avi`, `.mov`).

Should we integrate C/C++ media decoding libraries via JNI (e.g. JavaCV, Xuggler) or execute the native `ffmpeg` CLI binary as an external subprocess?

## Decision Drivers

- **Process Crash Isolation:** C library memory corruption or codec segmentation faults must never terminate the JVM editor process.
- **Maintenance & Upgradability:** Ability to leverage hardware acceleration (VA-API, NVENC) and newer codecs without recompiling native JNI shared libraries.
- **Portability:** Cross-distribution compatibility across Debian, Ubuntu, Fedora, and Arch Linux using system package manager binaries.

## Considered Options

1. **Option 1: JNI / JavaCV (Java bindings for FFmpeg C libraries):** Direct in-memory C function calls for frame-by-frame encoding.
2. **Option 2: Native `ffmpeg` CLI Subprocesses:** External process invocation via `java.lang.ProcessBuilder` with pipe redirection.

## Decision Outcome

Chosen **Option 2: Native `ffmpeg` Subprocesses** (implemented in `Exporter.java`).

### Consequences

#### Positive
- **Fault Tolerance:** If a corrupted video file causes FFmpeg to crash, the JVM catches the non-zero exit code and displays the error log without crashing the application.
- **Codec Independence:** Supports whatever codecs and hardware acceleration engines are compiled into the host's FFmpeg binary.
- **Zero JNI Headaches:** Eliminates complicated native library compilation, GLIBC version mismatches, and native heap leaks.

#### Negative
- **Disk I/O Overhead:** Multi-pass export requires writing intermediate video segments to `/tmp` before final mixdown.
- **CLI String Formatting:** Filtergraph arguments must be carefully escaped to satisfy FFmpeg's CLI parser.

For project persistence decisions, see [0003-aes-encrypted-xml-project-format.md](0003-aes-encrypted-xml-project-format.md).
