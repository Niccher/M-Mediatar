# Engineering: Contributing

Thank you for your interest in contributing to **MMediatar**! This document outlines our branch conventions, code review standards, and development guidelines.

## Branch Strategy

- **`master` / `main`:** Production-ready code. Must always compile and pass documentation quality checks.
- **`feature/<feature-name>`:** New capabilities (e.g. `feature/audio-eq-filter`).
- **`fix/<bug-description>`:** Bug fixes (e.g. `fix/wayland-scaling-offset`).
- **`docs/<topic>`:** Documentation additions and clarifications.

---

## Commit Message Conventions

We follow Conventional Commits formatting:

```
<type>(<scope>): <short description>

[optional body]

[optional footer(s)]
```

### Types
- `feat`: A new user-facing feature or export codec.
- `fix`: A bug fix or playback issue resolution.
- `docs`: Documentation changes, README updates, or diagrams.
- `refactor`: Internal code reorganization without changing behavior.
- `test`: Adding or modifying tests.
- `chore`: Build configuration or dependency version bumps.

---

## Coding Standards

### 1. Java 11 and JavaFX Style
- Use standard Java 4-space indentation.
- Never use wildcard imports (`import javafx.scene.control.*;` is discouraged; prefer explicit imports).
- Ensure all JavaFX scene graph modifications are executed on the JavaFX Application Thread:
  ```java
  Platform.runLater(() -> {
      timelinePane.redraw();
  });
  ```

### 2. Subprocess Safety
- **Never invoke `/bin/sh` or `/bin/bash` directly** when constructing FFmpeg or FFprobe commands.
- Always use `ProcessBuilder` with a discrete `List<String>` argument vector.
- Escape or validate any arbitrary text injected into FFmpeg filtergraphs (such as text overlays in the `T1` track).

### 3. XML Serialization Defensive Coding
- Never assume an XML element exists when loading a `.mediatar` file.
- Provide safe fallback values for missing properties to ensure projects created with older versions can be loaded without exceptions.

---

## Pull Request Checklist

Before submitting a pull request, ensure:
1. `mvn clean compile` succeeds without compiler warnings.
2. `python3 scripts/lint-docs.py .` passes with zero errors and zero warnings.
3. The Definition of Done checklist in [making-changes.md](making-changes.md) is satisfied.

For engineering troubleshooting, see [troubleshooting.md](troubleshooting.md).
