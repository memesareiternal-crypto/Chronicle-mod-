# Chronicle: Psychokinesis

**2.0.0-rebuild — Minecraft Forge 1.20.1, Java 17.** Manipulate targets through force, direction, scale, and context. Right click is the primary grip; there is no ability list or mode selector. An unlabeled graphical meter above the hotbar and experience bar shows the output you currently choose to use.

## Getting started

Install the jar from `build/libs/` on the client and server with Forge. Rare chambers generate in newly created Overworld chunks; sustained exposure to their resonant crystal awakens psychokinesis. Operators can use `/psychokinesis max` for testing or `/psychokinesis spawncrystal` to place a crystal.

The supplied crystal model and embedded textures are complete. The earlier incomplete appearance came from conversion/rendering issues in this project. Its corrected model now includes animated emissive cracks.

## Controls

| Input | Result |
| --- | --- |
| Hold right click while aiming | Grip and steer a mob, projectile, vehicle, block, or connected mass; works empty handed or with an item |
| Release right click | Release the grip and preserve momentum |
| Sneak + hold right click | Select a nearby group or larger connected block area |
| Hold G | Optional alternate grip; press G while already holding to add another aimed target |
| G + scroll with no held target | Adjust the selection radius |
| Scroll while holding | Change the holding distance |
| Hold left click + scroll while holding | Rotate the held object; block masses rotate in quarter turns |
| Hold/release left click while holding | Charge and throw; longer holds invest more force |
| Sneak + brief left click while holding | Disarm held living targets; continue past 12 ticks to compress |
| Sustain compression | Apply inward force; sufficiently advanced sustained grips can remove armor or shatter matter |
| Sprint + release while holding | Place a block mass, deposit a held item into a compatible container, or pin a target to the aimed surface |
| Right-click tap with no grip | Operate suitable remote blocks or harvest ripe crops |
| Right-click charge/release with no acquired grip | Apply directional pressure; sneak pulls inward, a long high-level release expands into a psychic explosion |
| Jump + release right click with no held target | Toggle acceleration-based flight; movement keys steer, jump rises, sneak descends, sprint accelerates |
| Sneak + jump + release right click with no held target | Sense nearby living targets |
| Sneak + sprint + right click | Shape a cutting gesture; aim movement and left click affect its geometry |
| Sprint + right click with a block item and no grip | Build along the aimed surface; sneak adds mirrored placement |
| Tap B | Raise or dismiss self-protection; dismissal redirects captured projectiles |
| Hold B and aim | Position protection around an entity or location |
| Sneak + B / sprint + B | Create a plane / dome instead of a sphere |
| B + scroll | Resize protection, including while forming a new barrier |
| Sneak + scroll, anywhere | Lower or raise overall output; this takes priority over other wheel contexts |
| Sneak + G + right click, with no held target | Toggle concentration |

Scrolling also continues to change the vanilla hotbar. Sneak-scroll adjusts output from 5% to 100% and updates the meter; it never selects an ability. Lower output reduces force, reach, carried mass, group size, flight, and defensive effort. Right-click item use is intercepted while concentration is active; toggling concentration restores normal item use.

## Scale, progression, and configuration

Any awakened player can run `/psychokinesis level` to see their stage out of 10, or `/psychokinesis controls` for control instructions. Operators additionally have `grant`, `remove`, `max`, `setlevel`, `setprogress`, `resetprogress`, `spawncrystal`, and `spawnformation` commands.

Default full-output progression allows one target at stage 1, two at stage 2, dozens at intermediate stages, and about 323 at stage 10, within the configurable 512-target ceiling. The tests grip and release 256 real mobs. At stage 10, the default selection budget reaches 12,288 block cells; the carrier bounding-volume ceiling is 32,768.

Edit `config/chronicle-common.toml`:

- `growth.levelingRateMultiplier`: `0`–`100`; `0` disables earned progression.
- `growth.maximumLevel`: `1`–`10`.
- `physics.overallStrength`: `0.1`–`20`, default `1`.
- `physics.outputScrollStep`: output adjustment per crouch-scroll step.
- `physics.maximumSimultaneousTargets`, `targetCountGrowthPerStage`, `maximumStructureVolume`, and `worldEditBudgetPerTick`: scale and workload limits.
- Force, reach, resistance, compression, collision damage, flight, barrier integrity, PvP, inventory transport, and crystal-generation settings.
- `presentation.specialEffects`, `thinAura`, and `restrainedPowerSounds`: optional presentation.

Existing config files keep their saved choices, including older structure limits. Strain reduces control when overused; it never directly damages the wielder.

## Rendering and world changes

Barriers and explosions use smooth translucent prismatic surfaces and soft pressure ripples. The films draw after translucent world blocks with additive blending and read-only depth, preserving the scene behind them. Powers do not emit particle effects or lock the camera. The graphical output meter spans the hotbar width above the experience bar and has no ability labels or level text.

A moving block selection is one simulated `MatterBody`. Its palette stores real block states and optional block-entity data; a cached mesh uses the resource pack's block textures. Extraction, placement, and other large edits share a dimension-wide work queue, defaulting to 256 work steps per tick. There are no independent ticking block entities for each carried cell.

See [architecture](ARCHITECTURE.md) and [compatibility and verification limits](docs/COMPATIBILITY.md) for the implementation and its practical limits.

## Build and verification

Use Java 17 with Forge 47.4.26:

```text
gradlew.bat build
gradlew.bat runGameTestServer
```

The production build and all **38 GameTests** pass. Coverage includes entity/block collisions, disarming controls, composite ownership, projectile barriers, queued inventory-safe transfers, hundreds-scale groups, output adjustment, cleanup, and flight permissions. The jar is `build/libs/psychokinesis-2.0.0-rebuild.jar`; test classes are excluded from its packaging. The client also verified the prismatic films, animated crystal glow, textured mass, and meter placement. Live multiplayer and TaCZ modpack validation remain separate checks.
