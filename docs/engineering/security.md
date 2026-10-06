# Engineering: Security Architecture

This document outlines the security architecture, input sanitization standards, and cryptographic implementations across **MMediatar**.

## Subprocess Command Sanitization

A critical security boundary in MMediatar is the execution of external CLI utilities (`ffmpeg` and `ffprobe`).

### Safe Process Invocation Pattern

**Rule:** Never concatenate shell strings or invoke `/bin/sh -c`.

```java
// SECURE PATTERN: Argument array passed directly to OS execve syscall
List<String> cmd = new ArrayList<>();
cmd.add("ffmpeg");
cmd.add("-y");
cmd.add("-i");
cmd.add(untrustedFilePath); // Cannot break out of argument boundary
ProcessBuilder pb = new ProcessBuilder(cmd);
Process p = pb.start();
```

Because arguments are passed as discrete elements in the `argv` array:
- Space characters in filenames do not require quotation marks.
- Characters such as `;`, `&&`, `|`, `$(...)`, or `` ` `` are interpreted literally as path characters by the filesystem, preventing shell command injection.

---

## FFmpeg Filtergraph Text Escaping

While command line arguments are safe from shell injection, FFmpeg's internal filtergraph parser interprets certain characters specially within filter strings (e.g. `drawtext` or `amix`).

### `drawtext` Filter Escaping Rules

When drawing text overlays from the `T1` track:
- **Colons (`:`)** separate filter parameters.
- **Single quotes (`'`)** delimit string literals.
- **Backslashes (`\`)** escape internal characters.

Any user-supplied text content must be escaped before injection into a filter string:

```java
public static String escapeDrawText(String input) {
    if (input == null) return "";
    return input.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace(":", "\\:");
}
```

---

## Project Encryption Implementation

Project state is encrypted on disk via `ProjectSerializer`:

| Attribute | Specification |
|---|---|
| **Algorithm** | Advanced Encryption Standard (AES) |
| **Cipher Transformation** | `AES/ECB/PKCS5Padding` |
| **Key Size** | 128-bit (16 bytes) |
| **Key Material** | `MediatarKeySec26` |

### Hardening Recommendations for Future Releases

1. **Migrate to AES-GCM:**  
   Replace `AES/ECB` with `AES/GCM/NoPadding` (Galois/Counter Mode) to provide authenticated encryption with integrity verification, preventing ciphertext tampering.
2. **User-Provided Passphrases:**  
   Derive encryption keys at runtime using PBKDF2 with SHA-256 and a random per-file salt rather than compiling a static key into the application bytecode.

For version compatibility and dependency matrices, see [release.md](release.md).
