# Build a Bridge — Plan

## Overview
A 1.12.2 Forge mod that adds a `/bridge` command to auto-generate a bridge in the player's viewing direction.

## Command Syntax
```
/bridge <length> <preset> [x y z]
```
- `length` — number of blocks the bridge extends (required)
- `preset` — bridge preset type (required), e.g. `railroad`
- `x y z` — optional starting position (defaults to player's position)

## Presets
### railroad
- Road: cobblestone (full row along bridge length)
- Rail: normal rail placed on top of road, oriented in the bridge direction
- Support beams: every 15–20 blocks, a cobblestone pillar goes down from the bridge
  until it hits a solid block. It replaces air/water/lava/glass and buries 5 blocks
  into the solid ground.

## Architecture
- `Buildabridge.java` — Main mod class, registers command on server starting
- `BridgeCommand.java` — Command implementation (extends CommandBase)
- `BridgeBuilder.java` — Core bridge building logic (placing blocks, support beams)
- `BridgePresets.java` — Preset definitions (railroad, etc.)

## Key Implementation Details
1. Command registered via `FMLServerStartingEvent.registerServerCommand()`
2. Direction determined from player's yaw (cardinal: N/S/E/W)
3. Bridge extends in the viewing direction from start position
4. Support beams at intervals of 15-20 blocks (configurable random)
5. Support beam goes down until solid block, then digs 5 deep into it
6. Replaces air, water, lava, and glass when building beams

## Edge Cases
- Player looking straight up/down → error message
- Non-player command senders → error
- Missing/invalid preset name → error
- Invalid coordinates → error
