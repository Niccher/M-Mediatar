# Runbook: Runtime Recovery and Restart

This runbook provides emergency recovery procedures for operators and developers when **MMediatar** hangs, crashes during export, or experiences media decoding locks.

## 1. Terminating Hanging Subprocesses

If an export operation freezes or the UI becomes unresponsive while FFmpeg is rendering:

### Identify Running Processes
```bash
pgrep -fl "ffmpeg"
pgrep -fl "Mediatar"
```

### Terminate Gracefully (SIGTERM)
```bash
pkill -f "ffmpeg"
pkill -f "com.nicch.mediatar.App"
```

### Force Kill if Locked (SIGKILL)
```bash
killall -9 ffmpeg
pkill -9 -f "com.nicch.mediatar"
```

---

## 2. Purging Orphaned Temporary Export Files

If an export crashes before reaching the cleanup phase, intermediate video and audio chunks remain in `/tmp`:

### Locate Orphaned Export Directories
```bash
ls -ld /tmp/mediatar_export*
```

### Calculate Wasted Disk Space
```bash
du -sh /tmp/mediatar_export*
```

### Safe Cleanup Command
```bash
rm -rf /tmp/mediatar_export*
```

---

## 3. Resetting GStreamer Registry Cache

If JavaFX Media begins failing with decoding errors after a system update or library upgrade, purge GStreamer's cached plugin registry:

```bash
# Remove user GStreamer registry cache
rm -rf ~/.cache/gstreamer-1.0/

# Rebuild plugin registry
gst-inspect-1.0 > /dev/null
```

---

## 4. Nuclear Application Reset

To completely reset the application environment and Maven compile artifacts:

```bash
# Navigate to project root
cd /home/niccher/NetBeansProjects/Mediatar

# Clean build artifacts
mvn clean

# Clear local temp directories
rm -rf /tmp/mediatar_export* target/

# Rebuild and launch fresh
mvn clean compile
mvn javafx:run
```

For project backup and restoration procedures, see [backup-and-restore.md](backup-and-restore.md).
