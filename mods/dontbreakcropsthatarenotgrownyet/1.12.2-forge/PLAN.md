# Plan: Fix NoSuchMethodError on EntityPlayer.isSneaking()

## Issue
When testing the mod in-game, every crop break attempt throws:
```
java.lang.NoSuchMethodError: net.minecraft.entity.player.EntityPlayer.isSneaking()Z
	at asd.itamio.dontbreakcropsthatarenotgrownyet.CropBreakHandler.onBlockBreak(CropBreakHandler.java:41)
```

## Root Cause
In the MCP stable_39 mappings used by ForgeGradle 2.3 for Minecraft 1.12.2, the method `isSneaking()` is declared on `EntityLivingBase`, not on `EntityPlayer`. The original code calls `event.getPlayer().isSneaking()` where `getPlayer()` returns `EntityPlayer`. The Java compiler generates bytecode referencing `EntityPlayer.isSneaking()`, but the MCP-to-SRG remapper only has the mapping for `EntityLivingBase.isSneaking()` → `EntityLivingBase.func_70093_af`. Because the remapper doesn't find the mapping on `EntityPlayer`, the method name stays in MCP form. At runtime, the game classes use SRG names, so `EntityPlayer.isSneaking()` doesn't exist in the method table.

## Fix
Call `isSneaking()` through an explicit `EntityLivingBase` reference or cast, so the Java compiler generates bytecode referencing `EntityLivingBase.isSneaking()`, which the MCP remapper correctly transforms to `EntityLivingBase.func_70093_af()`.

### Change
In `CropBreakHandler.java`:
- Add `import net.minecraft.entity.EntityLivingBase;`
- Change `event.getPlayer().isSneaking()` to `((EntityLivingBase) event.getPlayer()).isSneaking()`

## Testing
1. Build with `./gradlew clean build` in `.moditamio-cache/mods/dontbreakcropsthatarenotgrownyet/1.12.2-forge/`
2. Verify JAR builds
3. Update final state summary
