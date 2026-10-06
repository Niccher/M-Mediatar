# Engineering: Release and Compatibility Matrix

This document defines the compatibility matrix, semantic versioning rules, and release protocols for **MMediatar**.

## Compatibility Matrix

MMediatar dependencies must remain synchronized to ensure consistent desktop video decoding and native rendering performance:

| MMediatar Release | Target JDK | JavaFX Release | GStreamer Core | FFmpeg Baseline | .mediatar Schema |
|---|---|---|---|---|---|
| **1.0.0 (Current)** | OpenJDK 11, 17, 21 | 22.0.1 | 1.16+ / 1.18+ / 1.20+ | 4.4+ / 5.x / 6.x / 7.x | 1.0 (XML/AES-128) |
| **0.9.x (Beta)** | OpenJDK 11 | 21.0.2 | 1.16+ | 4.2+ | 1.0 (XML/AES-128) |

---

## Semantic Versioning Policy

MMediatar follows [Semantic Versioning 2.0.0](https://semver.org/):

$$\text{MAJOR}.\text{MINOR}.\text{PATCH}$$

- **MAJOR (e.g. 2.0.0):**  
  Incompatible changes to the `.mediatar` project serialization schema, removal of supported timeline tracks, or upgrading baseline JDK past Java 11.
- **MINOR (e.g. 1.1.0):**  
  Addition of new video/audio export presets, new editing tools (Slip/Slide), or new theme options while maintaining backwards compatibility with existing `.mediatar` project files.
- **PATCH (e.g. 1.0.1):**  
  Bug fixes, GStreamer native path adjustments, or rendering performance optimizations with zero schema or UI breakage.

---

## Backwards Compatibility Protocol

1. **Forward Compatibility of `.mediatar` Projects:**  
   New versions of MMediatar must always open and cleanly deserialize project files saved by older releases.
2. **Graceful Fallbacks:**  
   If an older release opens a project file created by a newer release, unknown XML tags must be discarded safely without crashing the DOM parser.
3. **Deprecation Window:**  
   Before retiring any export format or codec option, notice must be posted in release notes for at least one minor release cycle.

---

## Release Checklist

- [ ] Update `<version>` in `pom.xml`.
- [ ] Run complete test suite and verification smoke tests.
- [ ] Execute `python3 scripts/lint-docs.py .` to ensure documentation quality passes cleanly.
- [ ] Tag the git commit with `v<MAJOR>.<MINOR>.<PATCH>`.
- [ ] Generate standalone JAR artifact (`mvn clean package`).
- [ ] Verify test launch on clean Ubuntu, Fedora, and Arch installations.

For crash recovery and operational procedures, see [runbooks/restart.md](../runbooks/restart.md).
