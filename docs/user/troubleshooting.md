# User Guide: Troubleshooting

This guide resolves common runtime, playback, and rendering issues encountered when running **MMediatar** on Linux.

## Common Runtime Issues

### 1. `MediaException: MEDIA_UNAVAILABLE` on Video Import / Playback

**Symptom:**  
Importing a media file fails with a JavaFX exception `MediaException: MEDIA_UNAVAILABLE`, or video playback fails to load frames.

**Cause:**  
JavaFX Media requires GStreamer 1.0 native libraries. Either the packages are missing or the JVM was not pointed to the correct plugin path.

**Solution:**
1. Verify GStreamer libraries are installed:
   ```bash
   dpkg -l | grep gstreamer1.0  # Debian/Ubuntu
   rpm -qa | grep gstreamer1    # Fedora
   pacman -Q | grep gstreamer   # Arch
   ```
2. Locate `libgstreamer-1.0.so` on your machine:
   ```bash
   find /usr/lib* -name "libgstreamer-1.0.so*" 2>/dev/null
   ```
3. Set the JVM arguments explicitly when launching:
   ```bash
   mvn javafx:run -Doptions="-DGST_PLUGIN_PATH=/usr/lib/x86_64-linux-gnu/gstreamer-1.0 -Djava.library.path=/usr/lib/x86_64-linux-gnu/gstreamer-1.0"
   ```

---

### 2. `Cannot run program "ffmpeg": error=2, No such file or directory`

**Symptom:**  
Exporting a video or importing an uncompressed audio file immediately throws an `IOException` stating `ffmpeg` cannot be executed.

**Cause:**  
`ffmpeg` is either not installed or not exposed in the system `PATH` of the running process.

**Solution:**
1. Check if `ffmpeg` is available:
   ```bash
   which ffmpeg
   which ffprobe
   ```
2. If missing, install it:
   ```bash
   sudo apt install ffmpeg  # Debian/Ubuntu
   sudo dnf install ffmpeg  # Fedora
   sudo pacman -S ffmpeg   # Arch
   ```
3. If installed in a non-standard directory (such as `/usr/local/bin` or a flatpak runtime), ensure that directory is exported in your environment:
   ```bash
   export PATH="/usr/local/bin:$PATH"
   ```

---

### 3. Silent Audio During Preview

**Symptom:**  
Video plays smoothly and the VU meter bars bounce, but no audio is heard from the speakers/headphones.

**Cause:**  
PulseAudio or PipeWire is routing JavaFX output to a disconnected sink or the timeline audio track/master volume is muted.

**Solution:**
1. Check MMediatar's volume slider in the bottom right corner (ensure it is above 0% and the Mute toggle is deactivated).
2. Right-click the audio clip on track `A1` or `A2` and confirm `Toggle Mute` is not checked.
3. Open `pavucontrol` (PulseAudio Volume Control) and navigate to the **Playback** tab:
   ```bash
   pavucontrol
   ```
   Ensure Java / OpenJDK is unmuted and routed to your active output device.

---

### 4. UI Scaling Blur or Window Stutter under Wayland

**Symptom:**  
On high-resolution displays (4K/HiDPI) under GNOME or KDE Wayland sessions, the timeline canvas appears fuzzy or mouse coordinates feel offset.

**Cause:**  
JavaFX GTK glass backend can experience fractional scaling mismatches under native Wayland.

**Solution:**
Force integer UI scaling or execute under XWayland:
```bash
GDK_BACKEND=x11 mvn javafx:run -Doptions="-Dglass.gtk.uiScale=1.0"
```

---

### 5. Disk Space Exhaustion During Multi-Track Export

**Symptom:**  
Export fails during Phase 1 or Phase 4 with error `No space left on device`.

**Cause:**  
MMediatar generates intermediate lossless/near-lossless segment files in `/tmp/mediatar_export*`. On systems with `/tmp` mounted as a small `tmpfs` RAM disk, large multi-track renders may exhaust memory.

**Solution:**
Override the temporary directory used by Java by passing `java.io.tmpdir`:
```bash
mkdir -p $HOME/tmp
mvn javafx:run -Doptions="-Djava.io.tmpdir=$HOME/tmp"
```

---

For developer and build issues, refer to [Engineering Troubleshooting](../engineering/troubleshooting.md).  
For runtime restart and recovery procedures, see the [Restart Runbook](../runbooks/restart.md).
