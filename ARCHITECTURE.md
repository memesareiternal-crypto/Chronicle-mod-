# Architecture

Gameplay lives under `dev.chronicle`. Build interactions from shared forces and matter, not selectable abilities.

## Input and authority

`Presentation` samples G (gather/orbit), V (force/launch), B (protection), mouse buttons, movement and wheel sign. `Intent` bounds the 12-bit mask. Input packets contain no target IDs, coordinates, damage or world edits; the server chooses targets and performs edits.

`Concentration` owns reversible grips, pending terrain jobs, orbital centers, personal protection, projected wards and flight. Output automatically determines group/terrain radius and capacity. Crouch-scroll adjusts output without canceling vanilla hotbar scrolling. Target, tap/hold duration, aim motion and modifiers supply context.

G gathering persists after key release. Right click temporarily steers an orbit at the crosshair. V launches one member every three ticks or the full swarm with crouch. Volley gestures stay distinct from empty-hand force gestures, preventing an exhausted swarm from becoming an unintended explosion.

## Force, impacts and defense

`Physics.Hold` exclusively claims root vehicles and their passenger snapshots. It validates topology, combines mass/armor/health/boss resistance, suspends AI/gravity, and restores original state on release. A damped position-error solver responds to aim changes. Compression accumulates work; disarming removes real items before spawning them as loose equipment.

The momentum ledger handles swept entity impacts, blocked-axis wall impacts and cooldowns. Held groups do not attack one another. `Impacts` combines mass, material and speed, applies nearby knockback/damage, sends restrained pressure waves, and queues preserved secondary terrain displacement. A per-owner cooldown bounds repeated impact edits.

`PersonalDefense` reduces Forge damage-event amounts directly, including attacks with no projectile entity. B reinforces immediately. Output, stage, strength and extreme incoming energy determine resistance; no fatigue state exists. Optional passive defense reverses appropriately weak approaching projectiles. Orbiting real debris intercepts crossing projectiles and qualifying damage paths.

`Ward` supplies finite sphere, dome and plane geometry for projected protection, capture, return and compression. `ExplosionEvent.Detonate` removes affected blocks/entities whose blast paths cross a surviving ward, including containing an internal blast. Unknown projectile damage remains subject to authoritative hurt-event handling.

## Flight

Double Space toggles `FlightControl`. A shared client/server integrator computes aim-relative acceleration, strafe, ascent/descent, braking and hover. `Wire.FlightState` sends authoritative speed, acceleration, braking and original gravity; local prediction actually moves the vanilla client instead of repeatedly overwriting it with server velocity packets.

Survival permissions remain unchanged. `FlightGuard` clears only the legitimate controlled player's floating timeout. Logout, death, dimension change, deactivation and orphan recovery restore gravity.

## Terrain, transport and rendering

`Applications` provides ray targeting, automatic group selection, coherent debris gathering, directed/radial pressure, terrain raising/flattening, concentrated drilling/shearing, farming, remote interaction and construction.

`MassJobs` performs connected BFS or explicit-cell extraction, permission validation, reservation, cell transfer and placement over ticks. Seen cells are marked when queued, avoiding repeated BFS frontier work. `WorldActions` shares the dimension budget for other edits. Each cell has exactly one authoritative owner throughout transfer. Stored block-entity NBT stays server-side and survives interruption.

`MatterBody` is one physical carrier with palette states, relative positions, NBT, bounds, mass, material hardness, owner and rigid quarter-turn rotation. Large bodies use a bounded center collision proxy; small bodies use normal bounds. Client positions interpolate server updates.

`MatterRenderer` builds actual resource-pack block quads in bounded mesh pages (2,048 shared cells/frame), removes occluded faces, uploads GPU buffers and caches them by revision. Source fluids use still textures; custom animated block entities use static particle-texture boxes. No per-cell ticking entities are created.

`PsychicGeometry` draws smooth prismatic films and ripples after translucent blocks with additive blending/read-only depth. The player controls the camera. An unlabeled 182-pixel output strip sits above the hotbar/XP area. `CrystalRenderer` caches/deduplicates baked faces and draws each once, applying animated full-bright light to the original vein material without coplanar emission overlays.

## Persistence and compatibility

`Potential` stores acquisition, activation, stage, XP, output and color. Old exertion data is purged. Practice and online time award configurable XP; power use never reduces control or health.

`GunBridge` optionally resolves TaCZ bullet estimation and pre-hurt APIs without a dependency. Unrecognized APIs retain Forge hurt fallback. Ordinary damage resistance applies even when a gun's armor-piercing split bypasses geometric projectile capture.

`Chambers`, `CrystalSeed` and `ResonantCrystal` provide budgeted generation and awakening. GameTests plus isolated client/visual checks exercise systems. Every development test/helper is excluded from the production jar. See [compatibility](docs/COMPATIBILITY.md) for live integration limits.
