# Chronicle: Psychokinesis

A Forge 1.20.1 psychokinesis system built around target, direction, scale, and context. It has no ability menu, mode wheel, custom HUD, or particle spam.

## Controls

| Input | Result |
| --- | --- |
| Hold **G** while aiming | Grip and steer an entity, projectile, vehicle, block, tree, or block mass |
| Release **G** | Release held matter |
| **Sneak + G** | Expand the connected block selection; scroll while focusing to change its radius |
| Scroll while holding | Change distance; sneak-scroll rotates a held structure |
| Hold/release **right click** | Charge and release telekinetic output with either hand empty or an item held |
| Sneak + right click while holding | Compress a held target; sustained pressure can disarm, strip, or shatter |
| Sprint + right click while holding blocks | Place the held mass precisely at the aimed grid position |
| Right click with no grip | Remote interaction on a tap, then vector push; sneak reverses it into a pull |
| Long right-click charge | At sufficient level, releases a radial psychic explosion |
| Jump + right click | Toggle psychokinetic flight |
| Sneak + jump + right click | Sense nearby living targets |
| Sneak + sprint + right click | Concentrate force into a cutting sweep |
| Tap **B** | Toggle a barrier around the player |
| Hold **B** and aim | Protect an aimed entity or location |
| Sneak + **B** | Shape the barrier into a plane |
| Hold **B** and scroll | Resize an active barrier |

Right-click item use is intercepted while concentration is active so the same action works with an empty hand or any held item. Sneak + G + right click toggles concentration.

## Progression and configuration

Progression has ten readable stages. Every powered player can run `/psychokinesis level` and see `level N/10`; it requires no operator permission. Operators also have `grant`, `remove`, `max`, `setlevel`, `setprogress`, `resetprogress`, and `spawncrystal` subcommands.

`config/chronicle-common.toml` includes:

- `growth.levelingRateMultiplier` for overall advancement speed
- separate use and online-time experience rates
- `physics.overallStrength` from `0.1` to `20.0`, default `1.0`
- reach, force, mass, structure, terrain, barrier, flight, recovery, world generation, PvP, and compatibility settings
- `presentation.specialEffects` to toggle thin barrier, force-wave, and psychic-explosion geometry

Strain remains a limit at every level. Going over it reduces control and output stability but never directly damages the player.

## Large moving masses

A moved block selection becomes one `MatterBody` on the server. The server simulates one transform, velocity, collision body, ownership record, and placement transaction. The client renders only exposed cells through Minecraft's baked block renderer, preserving the real block textures without creating or ticking one entity per block.

## Crystal acquisition

Rare configurable chambers generate deep in newly created Overworld chunks. Exposure to their resonant crystal grants the power. The supplied GLTF and its two embedded textures are complete. Pixel checks confirmed our extracted PNGs are exact vertical flips of the embedded images, so the earlier conversion then flipped the UVs a second time. The corrected model removes that double flip, replaces the invalid old OBJ loader field, and uses normal scene lighting.

## Build and verification

Use Java 17:

```text
gradlew.bat build
gradlew.bat runGameTestServer
```

The current nine integrated Minecraft tests pass. The production jar is written to `build/libs/`.
