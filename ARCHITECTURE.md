# Architecture

## Input and authority

`Presentation` samples G, vanilla right click, B, movement modifiers, and wheel direction. `Wire.Input` only carries a bounded bit mask and wheel sign. The server performs every raycast, selection, force calculation, edit-permission check, and damage decision. No client packet can name a target or world coordinate.

`Concentration` is the context controller. It combines target type, held state, charge, aim, motion modifiers, area radius, and progression into operations. There is deliberately no public ability/mode state.

## Force and entity ownership

`Physics.Hold` exclusively owns a target while gripped, suspends AI/gravity reversibly, and steers toward a desired position based on mass, health, distance, resistance, force, and strain-limited control. Releases preserve momentum. Swept collision processing handles telekinetically accelerated entity impacts.

Vehicles retain their passenger graph, so moving a boat or minecart moves its riders as one Minecraft entity hierarchy. Projectiles use the same ownership and velocity operations.

## Blocks and large masses

`Applications.liftArea` grows a connected selection from the aimed block under a configurable and progression-scaled limit. `MatterBody` stores relative positions, block states, optional block-entity NBT, bounds, transform, velocity, owner, and orientation in one entity. Capture and placement validate before mutation and preserve all data atomically.

The server ticks one carrier. `MatterRenderer` finds exposed cells and submits their real baked block models to Minecraft's shared buffers. It never creates a rendered or ticking entity for each block. A future optimization will bake those calls into persistent client vertex buffers for very large surfaces.

## Barriers

`Ward` owns geometry, attachment/anchor, scale, capacity, integrity, caught projectiles, orbiting interceptors, and failure. A barrier can follow the player or entity, anchor in the world, or use plane geometry. Damage reduces integrity and can penetrate when its energy exceeds capacity. `Wire.View` sends only the presentation state required to draw clean world-space lines.

## Progression and strain

`Potential` persists ownership, activation, level, experience, strain, and aura color. Ten stages scale force, reach, mass, area, target count, and defenses. `levelingRateMultiplier` scales use and time experience. `overallStrength` multiplies the whole system from 0.1 to 20.

Strain never damages the wielder. Above the safe capacity, `Potential.control` progressively reduces steering and launch accuracy/strength. Rest recovers capacity.

## Networking and compatibility

`Wire` has direction-restricted intent, view, and transient geometry-effect messages. Visual effects are line geometry and can be disabled server-side. `GunBridge` is an optional reflective TaCZ adapter; the normal Forge hurt/projectile hooks remain the fallback.

## World generation

`Chambers` and `CrystalSeed` generate rare deep underground crystal blooms in newly generated Overworld chunks. Proximity exposure grants persistent potential. Crystal art is supplied GLTF content converted to Forge OBJ with its original base and glow textures.
