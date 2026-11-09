# CreateTNT 2.1.0 — announcement draft

Status: **draft, not posted.**

---

**CreateTNT 2.1.0 is live** — all six modern builds are up:

• 1.20.1 Fabric / Forge
• 1.21.1 Fabric / NeoForge
• 1.21.11 Fabric
• 26.1.2 Fabric

**Crossbow shells actually hurt now.** The “firework does no damage” bug had two causes: on Fabric a wall hit put the blast inside the block (damage dropped to 1), and on Forge/NeoForge the firework hurt handler was cancelling the shell’s own explosion damage. Both are fixed — a direct hit now does the same damage as the mod’s TNT.

**Also in 2.1.0:**
• Chain reactions stack damage properly and stagger by default (less lag). `maxChainDetonations` caps huge chains.
• `rocketShellBlockDamage=false` — shells still hurt mobs and show the plume, but leave terrain alone.
• `rocketShellExplosionsFromDispensers` and `rocketShellExplosionsFromOtherMods` control which rockets are enhanced.
• `scatterSolidBlocks` — optional flying-block shrapnel.
• `replaceVanillaExplosionVisuals` — client-side vanilla explosion particles replaced with the mod plume; works on servers that don’t have the mod.
• `enhanceAllExplosions` — generic hook for gun/missile mods that use the vanilla explode call.
• 1.20.1 builds target Java 17; 26.1.2 targets Java 25.

Legacy 1.8.9/1.12.2 builds are unchanged. Full config list is in each file’s changelog. If something still looks off, drop it in the comments with your Minecraft version + loader.
