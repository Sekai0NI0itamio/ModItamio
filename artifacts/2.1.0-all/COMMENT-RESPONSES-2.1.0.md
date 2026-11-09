# CreateTNT 2.1.0 — comment-section responses

Source: CurseForge comments for **Create big cannons - Explosive TNT** (56 comments, fetched 2026-11-01).

## Fixed in 2.1.0

| Comment | Request / report | What changed |
|---|---|---|
| user_gg913kvmsd2xkyfz | One TNT does more damage than a chain of 9; config did not help (1.20.1 Forge) | `chainDamageStacking` (default on) lets same-tick blasts stack instead of being eaten by the 10-tick hurt immunity. Primed-TNT chains now use `simultaneousChainDetonation` / `chainDetonationDelay` instead of always `setFuse(0)`. |
| saneskate | All TNT explodes at once and lags | `simultaneousChainDetonation` now defaults to `false` (2-tick stagger, nearest first) and `maxChainDetonations` caps a chain at 48. |
| Pavciox | ISS firework spell griefs the world; wants a config | `rocketShellExplosionsFromOtherMods = false` keeps mod-spawned fireworks vanilla; `rocketShellBlockDamage = false` keeps shell visuals + entity damage but removes terrain damage. |
| LaLuaCreatura | "Shrapnel next?" | `scatterSolidBlocks`, `blockScatterChance`, `blockScatterSpeed` launch destroyed solid blocks as flying debris. |
| jiaoliao | Blocks should fly with explosion direction like Realistic Explosion Physics | Same `scatterSolidBlocks` path uses the blast-ray energy and direction for debris velocity. |
| MinexInd / ghost666_2 / welksy_ / Kakuto_planko | Visual-only version, all explosions, usable on any server | `replaceVanillaExplosionVisuals` client mixin swaps vanilla `EXPLOSION`/`EXPLOSION_EMITTER` particles for the mod plume. It is client-side and works on servers without the mod. `enhanceAllExplosions` is the server-side equivalent for gun/missile mods. |
| MisukiKTH | TACZ compatibility | `enhanceAllExplosions = true` routes any block-breaking `ServerLevel.explode` through the enhanced blast, covering TACZ and similar mods that use the vanilla explode call. |
| ichim0 | Creepers + more destructive | Creepers were added in 2.0.0; destruction is already tunable with `tntStrength` and `explosionPowerScale`. |

## Core crossbow fix (this release)

- **Fabric:** a rocket that detonates against a wall sits inside that block; vanilla `Explosion.getSeenPercent` then returns 0 and damage collapses to 1. The shell now searches backward along the flight vector (then upward) for a clear origin (`rocketBlastOriginSearch`).
- **Forge / NeoForge:** the firework hurt handler cancelled every hit whose direct entity was the tracked rocket — including the mod's own `EXPLOSION` damage. It now only cancels `DamageTypes.FIREWORKS`, and the rocket is untracked before detonation.

## Known limitations (not silently "fixed")

| Comment | Why it is not in this build |
|---|---|
| HelloWorldBestSoldier | Sable sub-levels are a separate moving level. Sable has its own API and its own "Sable: Destructive" addon; a proper hook needs a Sable dependency. `enhanceAllExplosions` does not reach sub-level blocks. |
| Idistrosity | NeoForge does not exist for 1.8.9; there is no NeoForge build to make. |
| user_7m3s97p0xdhij2tv | Glass not breaking is 1.8.9-only. The 1.20.1+ blast breaks glass normally (low blast resistance). Legacy 1.8.9 code is untouched in this release. |
| spicylad87904 | The 1.12.2 launch crash was the wrong jar upload and was fixed in 2.0.0. Legacy code is untouched here. |
| cal_is_burning | ICBM is 1.12.2-only; `enhanceAllExplosions` is implemented for 1.20.1+. |
| MinexInd | A fully client-side *gameplay* version cannot replace server-side block damage; only the visuals can be client-side. `replaceVanillaExplosionVisuals` is that visual half. |
| user_2icsgp2yp2tsf7p868531 | SRP phase-4 balance is a gameplay preference; use `explosionPowerScale` / `tntStrength` to tune it. |

## New config keys (config/createtnt.txt)

```
rocketShellBlockDamage = true
rocketShellExplosionsFromDispensers = true
rocketShellExplosionsFromOtherMods = false
rocketBlastOriginSearch = 2.5
chainDamageStacking = true
maxChainDetonations = 48
scatterSolidBlocks = false
blockScatterChance = 0.35
blockScatterSpeed = 1.0
replaceVanillaExplosionVisuals = false
vanillaExplosionVisualPower = 12.0
enhanceAllExplosions = false
```

All keys live-reload; no restart needed.
