# Architecture

All gameplay lives under `dev.chronicle`. Extend the shared manipulation systems rather than adding selectable abilities.

## Authority and input

`Presentation` samples right click, left click, the optional G grip, B barrier control, movement modifiers, and wheel direction. `Intent` bounds the button mask and wheel sign. `Wire.Input` never carries target IDs, coordinates, damage, or world edits: the server chooses targets and computes results.

`Concentration` interprets held state, target context, charge duration, aim movement, output, and modifiers. It owns active grips, pending selections, barrier, flight, and pin anchors. Sneak-scroll adjusts persistent output globally while vanilla hotbar scrolling remains enabled. The only graphical interface is an unlabeled output meter; there is no ability selector or level indicator.

## Force, mass, ownership, and collision

`Physics.Hold` exclusively claims the root vehicle and its passenger snapshot. It validates topology, includes passenger mass and living resistance, and reversibly suspends root AI/gravity. Steering uses position error, current velocity, mass, distance, output, strain, armor, and boss resistance. Pinning uses a fixed world anchor. Compression accumulates work against mass and durability instead of periodically applying a fixed damage amount.

The shared momentum ledger covers held steering, pressure impulses, and throws. Swept bounds find entity impacts; blocked velocity components determine wall impact energy. Claims are released from the original snapshot, competing owners cannot overwrite them, and co-moving held group members do not harm each other. Projectile impact hooks suppress native impacts while captured; release restores motion and attribution without accumulating arrow damage buffs. Suspension markers recover orphaned gravity, AI, and fireball acceleration after reload.

`FlightControl` applies acceleration, directional steering, braking, and strain to the player. It preserves survival abilities. `FlightGuard` clears only vanilla floating timeout fields while legitimate server-controlled movement is active; it neither enables global flight nor grants creative permissions.

## Selection and grouped matter

`Applications` supplies server ray targeting, contextual group acquisition, pressure, cutting geometry, remote block interaction, inventory deposit, crop tending, and construction. `MassJobs` grows connected selections, validates and reserves cells, transfers snapshots, and queues deployment. `WorldActions` shares the same per-dimension work budget for other edits. Loaded-chunk checks, standard edit permissions, and Forge vetoes apply before mutations.

`MatterBody` contains palette-based states, relative positions, block-entity NBT, bounds, mass, hardness, owner, velocity, and quarter-turn orientation in one entity. Extraction and placement transfer cell ownership incrementally; interrupted work preserves remaining cells. Inventory data stays server-side. Bounding-volume and work limits are independent of cell-count progression.

`MatterRenderer` caches actual baked block quads in GPU vertex buffers per render layer and rebuilds on snapshot revision or resource reload. Source fluids use their still texture; animated block-entity-only models use static particle-texture boxes. The server simulates one carrier rather than an entity for each cell. Arbitrary tilt and terrain twisting are outside the latest requested scope.

## Barriers and presentation

`Ward` shares sphere, dome, and finite-plane geometry between boundary crossing and damage interception. It follows the player or another entity, or anchors at a world position. Dimensions, invested charge, output, strain, incoming energy, and finite integrity determine protection. Projectiles can penetrate, weaken the construct, or enter reversible capture. Captured projectiles orbit and can be redirected on dismissal.

`Wire.View` sends barrier geometry, integrity, impact, output, and flight/holding state. `Wire.Effect` carries short-lived pressure releases; `Wire.Snapshot` sends compact matter palettes without inventories. `PsychicGeometry` renders smooth prismatic surfaces and pressure ripples through a dedicated additive, read-only-depth render layer after translucent world blocks. This avoids darkening the view behind the films. The camera remains under ordinary player control. The 182-pixel unlabeled output meter sits above the hotbar/experience area. The resonant crystal uses corrected source artwork with animated emissive cracks. Presentation toggles affect effects, aura, and restrained sounds; powers send no particle packets.

## Persistence, progression, and compatibility

`Potential` stores acquisition, activation, stage, experience, strain, output, and color in persistent player data and copies it through death. Ten stages scale force and target/block capacity strongly; choosing lower output makes the same power precise. Overexertion reduces control without dealing self-damage. Use and online-time experience obey the configurable leveling multiplier.

`GunBridge` optionally resolves TaCZ's public bullet and pre-hurt APIs, including attacker-aware barrier geometry and combined headshot-adjusted damage before armor-piercing splits. Unrecognized APIs retain standard Forge impact/hurt fallback. See [compatibility](docs/COMPATIBILITY.md) for verified APIs and untested mod combinations.

`Chambers`, `CrystalSeed`, and `ResonantCrystal` provide budgeted deep-Overworld generation and proximity awakening. Forge GameTests cover interacting systems; production packaging excludes their classes.
