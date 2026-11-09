# CurseForge upload log — CreateTNT 2.1.0

Project: **Create big cannons - Explosive TNT** (`cbc-explosive-tnt`, project id `1646650`)
Uploaded: 2026-11-01 from `artifacts/2.1.0-all/`

| File | Minecraft | Loader | Java | Status |
|---|---|---|---|---|
| CreateTNT 2.1.0 - 1.20.1 Fabric | 1.20.1 | Fabric | 17 | Approved |
| CreateTNT 2.1.0 - 1.20.1 Forge | 1.20.1 | Forge | 17 | Approved |
| CreateTNT 2.1.0 - 1.21.1 Fabric | 1.21.1 | Fabric | 21 | Approved |
| CreateTNT 2.1.0 - 1.21.1 NeoForge | 1.21.1 | NeoForge | 21 | Approved |
| CreateTNT 2.1.0 - 1.21.11 Fabric | 1.21.11 | Fabric | 21 | Approved |
| CreateTNT 2.1.0 - 26.1.2 Fabric | 26.1.2 | Fabric | 25 | Approved |

All files are auto-published once approved and use the same 2.1.0 changelog.

## Notes

- **Duplicate rejected file:** `CreateTNT 2.1.0 - 1.21.1 Fabric` (file id `9392127`) was created by a retried submit. CurseForge rejected it as a duplicate of `9392105`. The author panel has no delete control for rejected files; it is hidden from users. Leave it, or ask CurseForge support to remove it.
- **26.1.2 Java:** originally uploaded as Java 21, then edited to **Java 25** to match `fabric.mod.json` (`java >=25`).
- **Legacy:** 1.8.9 and 1.12.2 stay on 2.0.0; no 2.1.0 jars were built for them.
- **Upload technique:** the upload page no longer exposes an `input.dzu-input`. The React upload handler was invoked through a temporary hidden file input + CDP `upload`, and the MUI autocomplete values were committed via the component's React `onChange` because option clicks are still dead. See the project memory note for the updated playbook.
