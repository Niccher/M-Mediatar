# Engineering Documentation

Welcome to the internal engineering documentation for **MMediatar**. This documentation is written for software engineers who need to understand, maintain, modify, and extend the video editor codebase.

If you are an operator or end-user who simply wants to run the application, start at [../README.md](../README.md).

## Quick Navigation

| I want to… | Go here |
|---|---|
| **Run the system without editing code** | [../README.md](../README.md) |
| **Understand system components & C4 architecture** | [architecture/overview.md](architecture/overview.md) |
| **Inspect FFmpeg subprocess pipes & event flow** | [architecture/communication.md](architecture/communication.md) |
| **Explore .mediatar XML schema & in-memory models** | [architecture/data-and-storage.md](architecture/data-and-storage.md) |
| **Package the desktop app (Maven, JAR, jpackage)** | [architecture/deployment.md](architecture/deployment.md) |
| **Review security boundaries & threat analysis** | [architecture/threat-model.md](architecture/threat-model.md) |
| **Review internal API & DTO contracts** | [api/contract.md](api/contract.md) |
| **Deep dive into the JavaFX / JVM desktop engine** | [services/jvm.md](services/jvm.md) |
| **Configure a local IDE (NetBeans, IntelliJ, VS Code)** | [engineering/local-development.md](engineering/local-development.md) |
| **Follow step-by-step recipes to modify code** | [engineering/making-changes.md](engineering/making-changes.md) |
| **Inspect AES-128 encryption & XML persistence** | [engineering/database.md](engineering/database.md) |
| **Execute unit, headless, and media loader tests** | [engineering/testing.md](engineering/testing.md) |
| **Understand CI quality gates & doc verification** | [engineering/ci.md](engineering/ci.md) |
| **Review code style & Git contribution workflow** | [engineering/contributing.md](engineering/contributing.md) |
| **Troubleshoot build and module resolution errors** | [engineering/troubleshooting.md](engineering/troubleshooting.md) |
| **Subprocess command sanitization & security** | [engineering/security.md](engineering/security.md) |
| **Check JDK, JavaFX, and FFmpeg version matrix** | [engineering/release.md](engineering/release.md) |
| **Recover from rendering crashes & clean /tmp** | [runbooks/restart.md](runbooks/restart.md) |
| **Back up projects & relink missing media** | [runbooks/backup-and-restore.md](runbooks/backup-and-restore.md) |
| **Understand key Architecture Decision Records** | [adr/0001-custom-canvas-timeline.md](adr/0001-custom-canvas-timeline.md) |

## Repository Layout

```
Mediatar/
├── pom.xml                     # Maven project descriptor (Java 11, JavaFX 22.0.1)
├── module-info.java            # Java Platform Module System (JPMS) descriptor
├── nbactions.xml               # NetBeans custom runner & GStreamer properties
├── TestMedia.java              # Standalone verification test for JavaFX Media
├── mkdocs.yml                  # Material for MkDocs portal configuration
├── scripts/
│   └── lint-docs.py            # Quality linter for project documentation
├── src/
│   └── main/
│       ├── java/com/nicch/mediatar/
│       │   ├── App.java                    # Vanilla Java launcher (bypasses FX classpath check)
│       │   ├── MMediatar.java              # JavaFX Application bootstrap & window lifecycle
│       │   ├── MainWorkhorse.java          # Primary UI controller, track manager & playback loop
│       │   ├── TimelinePane.java           # Custom Canvas-based multi-track timeline widget
│       │   ├── Exporter.java               # Multi-pass FFmpeg rendering pipeline orchestrator
│       │   ├── ExportConfig.java           # Export parameter transfer object & callback definition
│       │   ├── ExportDialogController.java # FXML dialog controller for render options
│       │   ├── ProjectSerializer.java      # AES-128 encrypted XML persistence (.mediatar)
│       │   └── ThemeManager.java           # Centralized CSS theme switcher
│       └── resources/com/nicch/mediatar/
│           ├── mainworkhorse.fxml          # Primary UI view tree
│           ├── export_dialog.fxml          # Export dialog view tree
│           ├── audio_thumbnail.png         # Audio track visual placeholder
│           ├── logo.png                    # Application raster branding
│           └── logo.svg                    # Application vector branding
└── docs/                                   # Software engineering documentation
```
