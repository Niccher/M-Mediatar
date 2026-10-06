# Engineering: Local Development

This guide helps software engineers configure an interactive development environment for working on **MMediatar**.

## Developer Prerequisites

In addition to the runtime prerequisites, developers require:
- **Java Development Kit:** OpenJDK 11 or later (`javac`, `jar`, `jlink`).
- **Build Tool:** Apache Maven 3.8+.
- **Scene Builder:** Gluon Scene Builder (recommended for editing `.fxml` layouts).
- **GStreamer Debug Tools:** `gstreamer1.0-tools` (for `gst-launch-1.0` and `gst-inspect-1.0`).

---

## IDE Setup

### 1. Apache NetBeans (Primary Configured IDE)

MMediatar contains a pre-configured `nbactions.xml` in the repository root:

```xml
<action>
    <actionName>run</actionName>
    <packagings><packaging>jar</packaging></packagings>
    <goals><goal>process-classes</goal><goal>org.codehaus.mojo:exec-maven-plugin:3.1.0:exec</goal></goals>
    <properties>
        <exec.vmArgs>-DGST_PLUGIN_PATH=/usr/lib/x86_64-linux-gnu/gstreamer-1.0 -Djava.library.path=/usr/lib/x86_64-linux-gnu/gstreamer-1.0</exec.vmArgs>
        <exec.args>${exec.vmArgs} -classpath %classpath ${exec.mainClass}</exec.args>
        <exec.mainClass>com.nicch.mediatar.App</exec.mainClass>
        <exec.executable>java</exec.executable>
    </properties>
</action>
```

- Simply open the repository folder in NetBeans (`File → Open Project`).
- Click **Run** (`F6`) or **Debug** (`Ctrl+F5`). NetBeans will automatically execute with GStreamer flags attached.

---

### 2. IntelliJ IDEA

1. Open project via `pom.xml`.
2. Go to **Run → Edit Configurations…**
3. Create a new **Application** configuration:
   - **Main class:** `com.nicch.mediatar.App`
   - **VM options:**
     ```bash
     -DGST_PLUGIN_PATH=/usr/lib/x86_64-linux-gnu/gstreamer-1.0 -Djava.library.path=/usr/lib/x86_64-linux-gnu/gstreamer-1.0
     ```
   - **Module:** `Mediatar`
4. Save and run.

---

### 3. Visual Studio Code

Create `.vscode/launch.json` in your local workspace:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Launch MMediatar",
      "request": "launch",
      "mainClass": "com.nicch.mediatar.App",
      "vmArgs": "-DGST_PLUGIN_PATH=/usr/lib/x86_64-linux-gnu/gstreamer-1.0 -Djava.library.path=/usr/lib/x86_64-linux-gnu/gstreamer-1.0"
    }
  ]
}
```

---

## GStreamer Debugging

If JavaFX media fails to play an imported clip, enable native GStreamer debug logs by setting `GST_DEBUG` before launching:

```bash
# Level 2 (warnings/errors) or Level 3 (info)
GST_DEBUG=2 mvn javafx:run
```

To test if a media format can be decoded natively by GStreamer on your system:
```bash
gst-play-1.0 /path/to/test_video.mp4
```

---

## Editing FXML Layouts

FXML files reside in `src/main/resources/com/nicch/mediatar/`:
- `mainworkhorse.fxml`: Main window layout (menu bar, timeline canvas, media list view, inspector, transport).
- `export_dialog.fxml`: Modal dialog for configuring render settings.

Always open these files in **Gluon Scene Builder** to verify component hierarchy and layout constraints before committing changes.

For the change recipe table and Definition of Done, see [making-changes.md](making-changes.md).
