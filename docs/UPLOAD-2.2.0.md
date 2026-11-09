# CurseForge upload log — CreateTNT 2.2.0

Project: **Create big cannons - Explosive TNT** (`cbc-explosive-tnt`, project id `1646650`)
Uploaded: 2026-11-09 from `artifacts/2.2.0-all/` via the CurseForge API (`upload.py`).

| File | Minecraft | Loader | Java | File ID | Status |
|---|---|---|---|---|---|
| CreateTNT 2.2.0 - 1.8.9 Forge | 1.8.9 | Forge | 8 | 9522365 | Uploaded |
| CreateTNT 2.2.0 - 1.12.2 Forge | 1.12.2 | Forge | 8 | 9522366 | Uploaded |
| CreateTNT 2.2.0 - 1.20.1 Fabric | 1.20.1 | Fabric | 17 | 9522377 | Uploaded |
| CreateTNT 2.2.0 - 1.20.1 Forge | 1.20.1 | Forge | 17 | 9522378 | Uploaded |
| CreateTNT 2.2.0 - 1.21.1 Fabric | 1.21.1 | Fabric | 21 | 9522379 | Uploaded |
| CreateTNT 2.2.0 - 1.21.1 NeoForge | 1.21.1 | NeoForge | 21 | 9522380 | Uploaded |
| CreateTNT 2.2.0 - 1.21.11 Fabric | 1.21.11 | Fabric | 21 | 9522381 | Uploaded |
| CreateTNT 2.2.0 - 26.1.2 Fabric | 26.1.2 | Fabric | 25 | 9522382 | Uploaded |
| CreateTNT 2.2.0 - 26.3 Fabric | 26.3 | Fabric | 25 | 9522384 | Uploaded |
| CreateTNT 2.2.0 - 26.3 Forge | 26.3 | Forge | 25 | 9522385 | Uploaded |
| CreateTNT 2.2.0 - 26.3 NeoForge | 26.3 | NeoForge | 25 | 9522386 | Uploaded |

All files use the same 2.2.0 changelog and are auto-published once approved.

## Build

- All 11 builds compiled on GitHub Actions only (run 42570309493, `createtnt-build.yml`). No local Gradle run.
- 1.8.9 job failed twice with transient JDK-download HTTP 504 on GitHub runners; succeeded on rerun.
- `artifacts/2.2.0-all/` holds the 11 jars downloaded from the Actions artifacts (excluding `-sources.jar`).

## Notes

- The API's `gameVersions` for e.g. `1.20.1` resolve to several IDs (Minecraft-type 9990 vs Addons-type 9993 vs generic 9994). The upload must use the Minecraft-type ID (9990 / 11779 / 14406 / 16082 / 17045); the generic/Addons IDs are rejected with errorCode 1009. The first batch's `upload.py` name-resolution picked the generic ID and was rejected once; the retry picked Minecraft-type IDs.
- New in 2.2.0: `tntBlockDamageScale`, `tntEntityDamageScale`, `creeperBlockDamageScale`, `creeperEntityDamageScale`, `rocketBlockDamageScale`, `rocketEntityDamageScale` (1.0 = full, 0 = off, 0.5 = half, range 0–4). Set a block scale to 0 for a plume-only blast.
