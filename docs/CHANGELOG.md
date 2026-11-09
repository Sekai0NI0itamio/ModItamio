# Changelog

Global changelog across all mods. One section per mod, newest version first.

## CreateTNT

### 2.2.0
- Added per-source damage sliders so you can disable or reduce block damage while keeping the smoke plume: `tntBlockDamageScale` / `tntEntityDamageScale`, `creeperBlockDamageScale` / `creeperEntityDamageScale`, `rocketBlockDamageScale` / `rocketEntityDamageScale` (1.0 = full, 0 = off, 0.5 = half). Set a block scale to 0 for the requested visual-only plume (e.g. no TNT block damage on 1.21.11) while explosions still show the full plume, blast rays and shake. 1.0 = 4.0 (0 = off). All values live-reload.
- `rocketShellBlockDamage` is now correctly wired on all loaders (it was a dead config key on Fabric/Forge/NeoForge except 26.3). Rocket block damage now honours both `rocketShellBlockDamage` and the new `rocketBlockDamageScale`.
- Legacy 1.8.9 / 1.12.2 builds receive the same TNT/creeper sliders and are version-bumped to 2.2.0 with the rest of the matrix.

### 2.1.0
- Crossbow/dispenser firework shells now deal full TNT-level entity damage. The blast origin is nudged out of the block a rocket hits (vanilla `getSeenPercent` was returning 0 and cutting damage to 1).
- Fixed Forge/NeoForge cancelling the mod's own shell damage through the firework hurt handler.
- Added `rocketShellBlockDamage` (no terrain griefing), `rocketShellExplosionsFromDispensers`, `rocketShellExplosionsFromOtherMods` (ISS/spell fireworks stay vanilla), and `rocketBlastOriginSearch`.
- Chain reactions now honour `simultaneousChainDetonation` / `chainDetonationDelay` for primed TNT, cap with `maxChainDetonations`, and stack damage with `chainDamageStacking` (a chain of 9 no longer loses to a single TNT).
- Added optional solid-block shrapnel: `scatterSolidBlocks`, `blockScatterChance`, `blockScatterSpeed`.
- Added client-side `replaceVanillaExplosionVisuals` / `vanillaExplosionVisualPower` (works on servers without the mod) and `enhanceAllExplosions` for gun/missile mods.
- Default chain mode is now one-by-one (2-tick stagger) to reduce lag spikes.

### 2.0.0
- Creepers now explode with the enhanced CreateTNT explosion.
- Rockets fired from crossbows can now explode.


## SmoothContainer

### 1.0.0
- Initial release.
- Defers container screen initialization over 2-3 frames.
- Pre-warms item render data to eliminate open-chest lag spikes.

---

_(Add new entries at the top of each mod's section as versions are released.)_
