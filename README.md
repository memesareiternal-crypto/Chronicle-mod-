# Chronicle: Psychokinesis

A fresh Forge 1.20.1 implementation of the supplied Chronicle-inspired design. Gameplay lives in `dev.chronicle`; only the crystal artwork and Forge project scaffolding were retained from the earlier project.

Two custom keys channel a shared physical system. There is no ability screen, custom HUD, particle trail, or gravity-power kit. Optional hints use Minecraft's existing action bar. Active players have a faint, randomly colored geometric aura.

## Install and awaken

Use Java 17 and Minecraft 1.20.1 with Forge 47.4.26. Install the built jar on both client and server. Rare basalt/calcite chambers generate in **new Overworld chunks**, deep underground. Stand within five blocks of their large crystal for four seconds to awaken. Configurable potential grows through use and time possessed while online.

Operators can run:

```text
/psychokinesis grant [players]
/psychokinesis max [players]
/psychokinesis remove [players]
/give @s psychokinesis:crystal_spawn_egg
```

The spawn egg creates the crystal entity; it is the only added item and is available in the creative Spawn Eggs tab. Natural chambers generate separately.

## Controls

| Input | Application |
| --- | --- |
| Hold G | Grab and steer aimed matter: entities, vehicles and passengers, items, projectiles, blocks, liquids, connected trees |
| Release G | Let held matter fall |
| Hold R, scroll, release R | Choose intent without executing it; ordinary scrolling still changes hotbar slots |
| Scroll while holding matter | Adjust distance |
| Sneak + scroll while holding | Quarter-turn structures; rotate entities |
| Hold/release R with matter | Charge and throw; view motion and player momentum contribute |
| Sneak + R while holding | Compress/choke; prolonged focus disarms, then strips armor at sufficient level |
| Sprint + release R while holding | Snap matter to the target grid position; an outline previews the footprint |
| Jump / sprint while holding G | Raise / lower the target |
| Sneak + G + R with empty hands of force | Toggle concentration; release both keys before using it again |

The intent dial contains Matter, Cut/Sculpt, Region/Terrain, Force Field, Remote Building, Harvest/Replant, Radar, Flight, and Psionic Explosion. They reuse the same two keys and physical ownership rules.

- **Matter:** tap R to interact remotely with doors, levers, buttons and redstone controls, or project force. Sneak reverses the force into a pull. Throw mobs through other mobs or into walls. Stronger and healthier creatures resist holds. Loose arrows, snowballs, eggs, flint and nuggets acquire contextual projectile behavior when thrown.
- **Cut/Sculpt:** Sneak + R + scroll chooses sweep, line, plane, pierce, whirl, volley, scissor or trace. G marks a line origin; holding R traces or repeats cuts. Cuts obey hardness and edit permissions. Wood yields matching planks, wool/webs yield string, melons yield slices, pumpkins are carved, sheep are sheared, and scissor cuts release leads.
- **Region/Terrain:** press G at two corners, then release R to lift the region as one physical body. Without a completed selection, R peels a surface patch; Sneak rotates the peeled patch. Holding jump when lifting mounts the body as a moving platform. Hold G to steer it.
- **Force Field:** R forms or releases a field. G anchors it to the aimed entity or point; Sneak + G resets the anchor to self. Fields hold acceptable projectiles, repel weak attackers, protect their enclosed volume, and break under excessive damage. Sneak compresses their contents. Releasing returns caught projectiles. Nearby items can serve as expendable interceptors.
- **Remote Building:** R places the block in your actual hand at range. G + R removes aimed blocks. Sneak mirrors placement; Sneak + G selects the mirror plane. Normal item consumption applies.
- **Harvest/Replant:** R harvests crops and replants using a seed deducted from the loot. Sneak widens the area. Produce moves toward you.
- **Radar:** R outlines nearby living targets briefly.
- **Flight:** R starts/stops flight using vanilla movement controls. Sustained flight consumes strain.
- **Psionic Explosion:** hold/release R for a radial pressure wave and capped terrain destruction. Sneak draws inward. No explosion particles are emitted.

## Progression and balance

Default unlocks: flight 8, fields 12, radar 18, regeneration 25, large regions 30, explosion 50. Basic manipulation, physical enhancement and sculpting are available immediately and grow with potential. Level raises force, reach, grip, defense and strain capacity. Exceeding that capacity always damages the wielder, even at maximum level; continuing to exert increases that damage. Rest restores capacity.

`config/chronicle-common.toml` controls growth, passive experience, strain/recovery/damage, reach, force, resistance, hardness, projectile capacity, structure limits, terrain budgets, inventory handling, PvP, field size, cooldowns, unlocks, crystal rarity/depth/exposure, aura and hints.

## Structures and compatibility

Structures retain block states and optional block-entity NBT in one carrier. Their visible surface is a cached, merged mesh using block map colors, avoiding per-block renderers and entities. Placement is atomic: an obstructed destination leaves all contents in the body. Rotation updates block-state orientation. Saved bodies resume with gravity after restart.

Vanilla projectiles use damage estimates for interception. Unknown projectile implementations use actual Forge damage events at impact. An optional TaCZ bridge checks combined bullet damage before its armor-piercing split and estimates moving TaCZ bullets using its API. TaCZ is not a required dependency; see [compatibility notes](docs/COMPATIBILITY.md) for verification limits.

## Build and verify

```text
./gradlew build
./gradlew runGameTestServer
```

Windows: use `gradlew.bat`. The production jar appears in `build/libs/`. GitHub Actions builds the jar, runs the Minecraft regression suite and uploads the jar and logs as artifacts. The suite covers inventory transfer, obstructed placement, movement, rotation, persistence, unbreakable blocks, finite strain, hold cleanup and input bounds.

Read [CONTINUATION.md](CONTINUATION.md) before resuming development. [Architecture](docs/ARCHITECTURE.md) and [current limits](docs/COMPATIBILITY.md) describe the implementation and remaining playtest work.

Code is MIT licensed. The supplied crystal assets are preserved in `assets/source/`.
