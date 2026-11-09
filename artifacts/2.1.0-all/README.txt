CreateTNT 2.1.0 — test builds
================================

All six modern builds are in this folder. Pick the jar that matches your
Minecraft version + loader. Do NOT install more than one.

  createtnt-1.20.1-fabric-2.1.0.jar    Minecraft 1.20.1  Fabric
  createtnt-1.20.1-forge-2.1.0.jar     Minecraft 1.20.1  Forge 47.x
  createtnt-1.21.1-fabric-2.1.0.jar    Minecraft 1.21.1  Fabric
  createtnt-1.21.1-neoforge-2.1.0.jar  Minecraft 1.21.1  NeoForge
  createtnt-1.21.11-fabric-2.1.0.jar   Minecraft 1.21.11 Fabric
  createtnt-26.1.2-fabric-2.1.0.jar    Minecraft 26.1.2  Fabric

Install: drop the jar in mods/ (Fabric builds need Fabric API). Launch once
to generate config/createtnt.txt. Every key is live-reloadable.

CROSSBOW TEST (the reported bug)
--------------------------------
1. Craft a crossbow and a firework rocket with at least 1 firework star.
2. Shoot an unarmoured zombie/cow point-blank and also shoot a wall next to
   a mob.
3. Expected: both blasts deal the same damage as the mod's TNT (one hit kills
   an unarmoured mob at close range). Before 2.1.0 the wall hit did 1 damage.

CHAIN TEST
----------
1. Line up 9 TNT blocks 1 block apart, light one.
2. Expected: the chain kills mobs across the line; one TNT alone does not
   out-damage the chain. If you prefer the old instant look, set
   simultaneousChainDetonation = true.

GRIEFING / SERVER TEST (Pavciox / ISS spell)
--------------------------------------------
rocketShellExplosionsFromOtherMods = false   -> mod-spawned fireworks stay vanilla
rocketShellBlockDamage = false               -> shell still hurts entities and shows
                                                the plume, but leaves terrain intact
rocketShellExplosions = false                -> all firework shell behavior off

VISUAL-ONLY TEST (works on a server without the mod)
----------------------------------------------------
replaceVanillaExplosionVisuals = true
Join any server (vanilla or modded), trigger a creeper/TNT explosion:
vanilla particles are replaced by the mod plume. No block/damage change.

SHRAPNEL TEST
-------------
scatterSolidBlocks = true
Blow up stone/dirt: a fraction of blocks fly out as debris
(blockScatterChance / blockScatterSpeed tune it).

GENERIC MOD COMPAT (TACZ / gun mods)
------------------------------------
enhanceAllExplosions = true
Any block-breaking ServerLevel explosion (not just TNT/creeper) uses the
enhanced blast. Default off because it also affects beds/crystals.

NOT INCLUDED
------------
- Sable sub-level damage (needs the Sable API; use "Sable: Destructive").
- 1.8.9 / 1.12.2 rebuilds (legacy code untouched).
