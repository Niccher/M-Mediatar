# Runbook: Backup and Restore

This runbook describes backup, disaster recovery, and offline XML diagnostics for **MMediatar** project files (`.mediatar`).

## 1. Project Backup Strategy

Because `.mediatar` files store references (absolute filesystem paths) to raw media clips rather than embedding the raw video payloads:
- A complete project archive requires backing up **both** the `.mediatar` project file and the directory containing the source media clips.

### Creating a Self-Contained Project Archive

To create a portable tarball containing the project and all associated media:

```bash
#!/bin/bash
PROJECT_NAME="my_video_project"
PROJECT_FILE="/path/to/${PROJECT_NAME}.mediatar"
MEDIA_DIR="/path/to/project_media"
BACKUP_DIR="$HOME/MediatarBackups"

mkdir -p "$BACKUP_DIR"
tar -czvf "${BACKUP_DIR}/${PROJECT_NAME}_$(date +%Y%m%d_%H%M%S).tar.gz" \
    "$PROJECT_FILE" \
    "$MEDIA_DIR"
```

---

## 2. Offline Diagnostics: Decrypting `.mediatar` via OpenSSL

If a `.mediatar` file becomes corrupted or fails to load in MMediatar, you can decrypt the underlying XML structure directly using the OpenSSL CLI.

The static 128-bit key string `"MediatarKeySec26"` corresponds to the hexadecimal string:
`4d656469617461724b65795365633236`

### Decrypting Ciphertext to Raw XML

```bash
openssl enc -d -aes-128-ecb \
    -K 4d656469617461724b65795365633236 \
    -in corrupted_project.mediatar \
    -out recovered_project.xml
```

You can now inspect `recovered_project.xml` using any text editor or validate its syntax:
```bash
xmllint --format recovered_project.xml
```

### Re-Encrypting Repaired XML back to `.mediatar`

```bash
openssl enc -e -aes-128-ecb \
    -K 4d656469617461724b65795365633236 \
    -in recovered_project.xml \
    -out repaired_project.mediatar
```

---

## 3. Relinking Moved Media Files

If media files were relocated to a new directory or transferred to another workstation:

1. Decrypt the project file to XML as described above.
2. Perform a search-and-replace on the directory path:
   ```bash
   sed -i 's|/old/path/to/media|/new/path/to/media|g' recovered_project.xml
   ```
3. Re-encrypt the XML file to `.mediatar`.
4. Open the project in MMediatar. The clips will immediately reconnect to their new paths.

For emergency application resets, see [restart.md](restart.md).
