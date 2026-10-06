# ADR 0003: AES-Encrypted XML for Project Persistence

- **Status:** Accepted
- **Deciders:** MMediatar Architecture Team
- **Date:** 2026-09-15

## Context and Problem Statement

MMediatar needs to save and load project state (media pool file paths, 5-track layout, clip offsets, durations, speed settings, volume levels, and text overlays).

Should project files be stored as open JSON/YAML, binary Java serialization, an embedded SQLite database, or AES-encrypted XML?

## Decision Drivers

- **Zero Third-Party Dependencies:** Must serialize and deserialize using built-in JDK standard libraries (`java.xml` and `javax.crypto`).
- **Data Integrity & Obfuscation:** Prevent accidental corruption or manual editing that breaks timeline invariants.
- **Portability:** Single-file archive easily shared between Linux workstations.

## Considered Options

1. **Option 1: Java Standard Object Serialization:** `ObjectOutputStream` / `Serializable`.
2. **Option 2: Embedded SQLite Database:** Local SQL database file storing relational tables.
3. **Option 3: Cleartext JSON / YAML:** Plain text structured documents (requires Jackson or Gson).
4. **Option 4: AES-128 Encrypted XML Document:** W3C DOM XML tree encrypted using standard JDK AES cipher.

## Decision Outcome

Chosen **Option 4: AES-128 Encrypted XML Document** (`.mediatar`, implemented in `ProjectSerializer.java`).

### Consequences

#### Positive
- **No External Dependencies:** Uses `javax.xml.parsers.DocumentBuilder` and `javax.crypto.Cipher` bundled in OpenJDK 11.
- **Backwards Compatibility:** XML schema enables optional tags and graceful fallback parsing when new properties are added.
- **Tamper Resistance:** Prevents casual modification or corruption of clip offsets outside the editor.

#### Negative
- **Key Invalidation Risk:** With a static compiled key, if the key is altered in a future version, old project files become inaccessible unless a migration path is provided.
- **Manual Diagnostics Overhead:** Troubleshooting corrupted project files requires decrypting via OpenSSL before inspecting the XML tree.

For backup and offline XML diagnostics, see [Runbook: Backup and Restore](../runbooks/backup-and-restore.md).
