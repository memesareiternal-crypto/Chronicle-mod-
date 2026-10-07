# Chronicle: Psychokinesis

**3.0.0-tatsumaki — Minecraft Forge 1.20.1, Java 17.** Overwhelming control over enemies, debris, terrain and your own movement. Three keys work alongside the mouse. There are no ability menus, power items, or named modes to scroll through.

## Start playing

Install `psychokinesis-3.0.0-tatsumaki.jar` on client and server. Rare chambers generate in new Overworld chunks; exposure to their crystal awakens the power. Operators can use `/psychokinesis max` to immediately test maximum potential.

The original crystal model and embedded textures are intact. Rendering caused the visual glitches. Each face now draws once, with animated full-bright white veins; the old overlapping additive glow pass is removed.

## Controls

| Input | What happens |
| --- | --- |
| Hold right click | Grab and steer the aimed entity group or connected terrain mass automatically, with any item or an empty hand |
| Release right click | Release ordinary grips; an existing orbit resumes |
| Hold then release left click while gripping | Charge and throw the entire selection toward your aim |
| Crouch + left click while gripping | Immediately disarm living targets; keep holding to crush; prolonged advanced control can strip armor |
| **G** | Gather nearby entities and cohesive ground fragments into an orbit; press again to release |
| Sprint + G | Gather around the aimed point instead of yourself |
| Right click during an orbit | Bring the swarm under direct aim control |
| **V** with held targets | Rapidly launch one target/fragment every three ticks |
| Crouch + V with held targets | Launch the entire swarm at once |
| Hold/release **V** with no held targets | Apply directional force; stronger charged impacts displace terrain |
| Crouch + hold/release V with no held targets | Pull an area toward yourself; advanced output raises a real terrain mass into suspension |
| V + left click with no held targets | Focus force into piercing/drilling; sweep the camera to shear a wider plane |
| Sprint + hold/release V | Radial pressure; charged advanced output erupts surrounding terrain into a psychic explosion |
| Charged V while looking down at the ground | Lower and flatten the surface by lifting its excess into one real displaced mass |
| **B** | Immediately toggle personal damage resistance |
| Hold B for half a second, aim, then release | Project a field around an ally or location; crouch forms a plane, sprint forms a dome |
| B + scroll | Resize an existing projected field |
| Tap B with a projected field | Dismiss it and return captured projectiles |
| **Double-tap Space** | Toggle self-flight, unlocked at level 4 by default |
| WASD / Space / crouch in flight | Steer / ascend / descend; no movement input brakes into hover; sprint accelerates |
| Crouch + scroll | Adjust output from 5% to 100%; ordinary hotbar scrolling also continues |
| Scroll while gripping | Change holding distance |
| Left click + scroll while gripping | Rotate objects; block masses rotate in quarter turns |
| Sprint + release right click | Place a terrain mass, deposit an item into a compatible container, or pin a target |
| Right-click tap on a utility block or ripe crop | Remote interaction or harvest |
| Sprint + right click with a block item | Place real inventory blocks remotely; crouch adds mirrored placement |

Mouse movement directly changes target positions. Low output selects individual blocks and a few entities; high output automatically acquires large groups and terrain. You do not manually select sections.

Use `/psychokinesis toggle` to switch concentration off/on and restore ordinary item use. Normal inventory screens, hotbar slots, and hotbar scrolling remain available. The only custom HUD is the unlabeled prismatic output strip above the hotbar/experience area.

## What the power can do

- Control mobs, players, bosses, projectiles, loose items and occupied vehicles with reversible gravity/AI suspension and exclusive ownership.
- Scale from one/two targets to dozens and hundreds. Default maximum potential/output permits about 323 targets, within a configurable 512-target ceiling.
- Lift up to 32,768 real block cells at default maximum output, including connected hills, platforms, trees and building fragments. Range reaches about 167 blocks by default, with a configurable maximum of 256.
- Orbit coherent debris around yourself or a chosen point, redirect incoming arrows using those blocks, and launch individual fragments or the full swarm.
- Throw with mass/output-dependent speed and momentum. Strong wall impacts damage nearby entities, knock them back, produce a pressure wave, open real cavities and eject secondary terrain.
- Lift ground, lower/flatten surfaces, punch cavities, drill trenches, rip structures and relocate preserved masses for construction.
- Instantly reinforce personal defense against ordinary melee, projectiles, explosions and damage-event-based modded attacks. At level 10/full output, B resists up to 94% of ordinary damage; extreme attacks penetrate more. Void/administrative death still applies.
- Optionally reverse incoming projectiles passively. Projected sphere/dome/plane fields have finite integrity, intercept/capture projectiles and suppress explosion paths crossing their surfaces. They can also contain an explosion inside a sphere.
- Apply concentrated armor-bypassing magic pressure while leaving resistance and exceptional defenses effective. Boss health, armor and mass contribute resistance; bosses are not blanket-immune.
- Briefly sense nearby living targets when gathering, heal at advanced stages, disarm real weapons, tend crops, operate remote redstone and transport inventory-bearing blocks.

**There are no overuse mechanics.** Repeated use never causes fatigue, self-damage, stamina depletion, recovery requirements or loss of control. Old exertion save data and config entries are removed. Practice and online time still award experience.

## Configuration and commands

Edit `config/chronicle-common.toml`:

- `growth.levelingRateMultiplier`: 0–100; zero disables earned progression.
- `physics.overallStrength`: 0.1–20, default 1.
- `physics.outputScrollStep`, `maximumSimultaneousTargets`, `maximumSelectionRadius`, `maximumStructureVolume` and `worldCellWorkPerTick`.
- `physics.initialControlReach`, `controlReachPerStage`, `maximumControlReach`, `initialCarriedBlocks`.
- `sustainedPower.selfFlightSpeed`, `selfFlightSpeedPerStage`, `selfFlightAcceleration`, `flightBraking`, `personalProtection`, `passiveProjectileDefense`.
- Force, collision/compression, boss resistance, terrain editing, PvP, inventory transport, unlock levels and crystal generation.
- Optional smooth prismatic effects, aura and restrained sounds.

The new reach, carried-block, work-budget and flight keys adopt this revamp's defaults when upgrading. Strength, leveling, protection and other unchanged config choices remain saved.

Any awakened player can use `/psychokinesis level` (level out of 10), `controls` and `toggle`. Operators also have `grant`, `remove`, `max`, `setlevel`, `setprogress`, `resetprogress`, `spawncrystal` and `spawnformation`.

## Performance and visuals

Moving terrain uses one carrier per cohesive fragment, real resource-pack block textures, compact palettes and preserved server-side container data. There is no ticking entity for each carried block. Server edits share a dimension-wide budget of 2,048 work steps per tick. Client mesh baking shares a 2,048-cell budget per frame, then reuses cached GPU buffers.

Smooth prismatic surfaces use additive blending and read-only depth so blocks behind fields keep their lighting. Power effects have no wire cages or particle spam. The camera stays under player control. Arbitrary terrain twisting/tilting remains excluded.

See [architecture](ARCHITECTURE.md) and [compatibility](docs/COMPATIBILITY.md) for coarse collision, custom block-entity, boss-script and modpack limits.

## Build and validation

Use Java 17 and Forge 47.4.26:

```text
gradlew.bat build
gradlew.bat runGameTestServer -PcleanGameTests
```

All **51 Minecraft GameTests** pass locally. They cover actual automatic right-click crowd control, a 1,728-cell terrain lift, debris interception, flattening, inventory-safe impact displacement, projected explosion suppression, disarming, ownership, output, unlimited use and flight gestures.

An isolated real client test pressed the actual keyboard controls and confirmed about 89 blocks of forward flight, 26 blocks of strafing, 27 blocks of ascent, braking, restored gravity and matching server positions. Client screenshots checked the crystal's two glow phases, textured carriers, prismatic film and meter placement. Test helpers are excluded from the production jar.

Live dedicated multiplayer and installed TaCZ/modpack acceptance still need testing; generic Forge damage resistance and the optional TaCZ adapter are implemented.
