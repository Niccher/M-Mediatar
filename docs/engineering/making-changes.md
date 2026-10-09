# Engineering: Making Changes

This document provides a task-oriented recipe table, modification workflows, and the mandatory Definition of Done checklist for modifying **MMediatar**.

## Task-to-File Matrix

| If you want to… | Touch |
|---|---|
| **Add a new export resolution or codec** | `ExportDialogController.java` (combo items) + `Exporter.java` (codec flags) |
| **Add a new timeline tool (e.g. Slip, Slide)** | `TimelinePane.java` (`ToolMode` enum, cursor handlers, mouse listeners) + `mainworkhorse.fxml` (toolbar button) |
| **Add a new clip property (e.g. volume curve)** | `MainWorkhorse.TimelineClip` (state) + `ProjectSerializer.java` (XML tag) + `Exporter.java` (FFmpeg filter) |
| **Adjust magnetic snapping distance** | `TimelinePane.java` (`SNAP_THRESHOLD` constant) |
| **Modify UI color theme or add a new theme** | `ThemeManager.java` (`Theme` enum and inline CSS styling) |
| **Change timeline keyboard shortcuts** | `MainWorkhorse.java` (`setupKeyboardShortcuts()` or `onKeyPressed` handler) |
| **Modify AES encryption key or cipher mode** | `ProjectSerializer.java` (`KEY_BYTES` and cipher initialization) |
| **Change default window dimensions or app icon** | `MMediatar.java` (`applyAppIcons()` method) + `src/main/resources/com/nicch/mediatar/icon.png` |

---

## Step-by-Step Recipes

### 1. Adding a New Video Export Codec (e.g. AV1 via `libsvtav1`)

1. **Update FXML UI Controller (`ExportDialogController.java`):**
   Add `"libsvtav1"` to the video codec combo box options in `initialize()`.
2. **Update Rendering Engine (`Exporter.java`):**
   Ensure `mixCmd.add("-c:v")` handles `"libsvtav1"` and appends appropriate quality preset parameters (e.g. `-crf 30 -preset 6`).
3. **Update Contract Reference:**
   Document the new codec in `docs/api/contract.md` and user options in `docs/user/configuration.md`.

---

### 2. Adding a New Inspector Slider (e.g. Clip Rotation)

1. **Add FXML UI Node (`mainworkhorse.fxml`):**
   Add a `Slider` and `Label` inside `videoPropertiesBox` with `fx:id="rotationSlider"`.
2. **Bind in Controller (`MainWorkhorse.java`):**
   - Inject `@FXML private Slider rotationSlider;`.
   - In `bindClipToInspector()`, link `rotationSlider.valueProperty()` to `activeClip.setRotation()`.
   - Update `mediaView.setRotate()` in the preview player.
3. **Persist Property (`ProjectSerializer.java`):**
   - In `saveProject()`, write `<rotation>` under `<clip>`.
   - In `loadProject()`, parse `<rotation>` and assign to the restored clip.
4. **Render Property in FFmpeg (`Exporter.java`):**
   - Add `rotate=...:ow=...:oh=...` to the video filtergraph for Phase 1 and Phase 5 passes.

---

## Definition of Done (DoD)

Before any feature branch or pull request is merged into `master`:

- [ ] **Code Compiles Cleanly:** `mvn clean compile` succeeds with zero errors.
- [ ] **Persistence Backwards-Compatibility:** `ProjectSerializer` successfully loads legacy `.mediatar` project files without crashing.
- [ ] **Subprocess Verification:** FFmpeg pipeline executes without hanging or throwing pipe stream exceptions.
- [ ] **No Shell Metacharacter Leakage:** Any new subprocess arguments are passed as discrete elements in `ProcessBuilder` argument lists.
- [ ] **Documentation Updated:**
  - If a user-facing command or config changed, update `README.md` and `docs/user/configuration.md`.
  - If internal contracts changed, update `docs/api/contract.md`.
- [ ] **Linter Cleanliness:** Execute `python3 scripts/lint-docs.py .` with zero errors and zero warnings.

For details on project serialization and storage, see [database.md](database.md).
