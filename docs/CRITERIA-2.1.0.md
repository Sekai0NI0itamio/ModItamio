# Criteria: CreateTNT 2.1.0 (comment-driven fixes)

## Crossbow / firework blast

- [x] A crossbow firework with explosion stars deals entity damage equal to a TNT blast of the same config (point blank on an unarmoured mob = same damage as the mod's TNT), not 1 damage.
- [x] Blast origin is nudged out of a solid block when the rocket detonates on impact, so `getSeenPercent` is not 0.
- [x] On Forge 1.20.1 and NeoForge 1.21.1 the mod's own explosion damage is not cancelled by the firework hurt handler.
- [x] `rocketShellBlockDamage = false` leaves terrain untouched but keeps entity damage and visuals.
- [x] `rocketShellExplosionsFromOtherMods = false` (default) stops mod-spawned fireworks (e.g. ISS spell) from using the shell blast.
- [x] `rocketShellExplosionsFromDispensers` controls dispenser rockets independently.

## Chain detonation

- [x] `simultaneousChainDetonation = false` staggers primed-TNT chain fuses by `chainDetonationDelay` (not all fuse 0).
- [x] `chainDamageStacking = true` lets multiple blasts in the same tick deal cumulative damage (chain of 9 > one TNT).
- [x] `maxChainDetonations` caps the chain to avoid lag spikes.

## Comment-requested features

- [x] `scatterSolidBlocks` + `blockScatterChance` + `blockScatterSpeed` launch destroyed solid blocks as debris (default off).
- [x] `replaceVanillaExplosionVisuals` renders the mod's plume for vanilla explosions client-side (default off); works on a server without the mod.
- [x] `enhanceAllExplosions` can route non-TNT/non-creeper `ServerLevel.explode` calls through the enhanced blast (default off; generic TACZ/ICBM-style compat).
- [x] Config file documents every new key and live-reloads.

## Mechanical

- [x] `gradle build` succeeds for 1.20.1-fabric, 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge, 1.21.11-fabric, 26.1.2-fabric.
- [x] All six jars land in `artifacts/2.1.0-all/`.
- [x] Changelog updated; no legacy 1.8.9/1.12.2 code touched.

## Not verifiable here

- Runtime in-game damage numbers (no Minecraft client launch in this environment) — report as manual test steps.
- Sable sub-level compat (needs Sable API; documented as a separate compat addon).
