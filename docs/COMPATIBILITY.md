# Compatibility and verification limits

Version 3.0.0 passes 51 Minecraft GameTests plus real-client flight and renderer checks. These do not establish every external mod or multiplayer combination.

## Damage and projectiles

Personal reinforcement reduces ordinary Forge living-hurt amounts directly, including melee, explosions, magic and attacks without projectile entities. B activates immediately. Generic mod-style magic damage is tested. Sources deliberately bypassing invulnerability (void/administrative death) are excluded.

Passive incoming-projectile reversal is configurable. Known vanilla projectiles have damage estimates; unknown implementations may supply numeric persistent `chronicle_damage`, or retain authoritative hurt-event evaluation. Their special movement, explosion or gun behavior can need an adapter. Orbiting real debris and projected fields use geometric crossing and finite thresholds.

The optional TaCZ adapter resolves `EntityKineticBullet.getDamage(Vec3)` and `EntityHurtByGunEvent.Pre` with attacker, base damage and headshot multiplier. It evaluates combined damage before armor-piercing splits; ordinary damage resistance still applies to uncanceled Forge hurt events. Different TaCZ versions, live gun packs and custom hitscan/AP/explosive paths remain untested. Mods that apply damage outside ordinary Forge living-hurt hooks cannot be guaranteed compatible.

Projected fields suppress/contain blast paths through Forge's detonate event. Mods that perform their own terrain deletion outside that event need their own integration.

## Matter and world edits

- Actual baked resource-pack block quads are cached in bounded GPU mesh pages. Animated block-entity-only renderers use static particle-texture boxes; server NBT is preserved, but custom machine animations do not run while airborne.
- Fluids use grid cells and still textures, not freeform fluid physics.
- Small bodies use coarse normal bounds; large bodies use a bounded center collision proxy. Exact concave surface collision, elastic twisting/tilting and structural support simulation are absent. Large bodies can overlap surrounding blocks at their edges.
- Standard block-state quarter-turn rotation is preserved. Additional machine orientation, multiblock/network relationships and external position references inside modded NBT need installed-mod checks.
- Loaded-chunk checks, world border, edit permission, Forge break-event vetoes and queued-cell reservations apply. Transfers preserve each cell and its inventory once, including interrupted work. Third-party claim/placement APIs may need adapters.
- Boss mass, health and armor provide resistance instead of blanket immunity. Scripted movement and multipart implementations can still override ordinary entity steering.
- Sensing uses brief vanilla glowing, which other clients can see.
- High counts are bounded, but hundreds of moving modded entities or complex resource-pack quads still need profiling on the intended server/client hardware.

## Rendering and flight

Prismatic surfaces use additive blending/read-only depth after translucent blocks and do not change the camera. Shader-mod ordering and third-party resource packs remain integration checks. Original crystal assets are intact; single-pass animated emission removes the old glow overlay.

Flight uses a shared integrator, real local movement prediction and server-synchronized capabilities; it never grants creative permissions. An isolated actual client test verified keyboard/network movement, sideways/vertical control, hover/braking and restored gravity. Dedicated server latency, anti-cheat mods and competing player-control mods remain unverified.

## Manual acceptance

1. Awaken near a crystal; inspect pulsing white veins, films and block lighting with the intended shader/resource pack.
2. Crouch-scroll low/high output and verify the unlabeled meter and normal hotbar changes.
3. Right click groups and terrain without crouching or selecting sections. Test stage 1/2/6/10, aiming, release, disarm, crush and powerful throws.
4. G gather self/point orbits; V individual/rapid/all launches; verify real debris defense and no accidental force release after a volley.
5. Raise/flatten ground, drill trenches, burst and throw large masses; inspect craters, secondary preserved debris and inventory counts.
6. Double Space to fly, move/strafe/rise/descend/brake while carrying matter; test survival on an allow-flight=false dedicated server.
7. B immediate resistance and projected sphere/dome/plane against ordinary and extreme damage, explosion containment and captured-projectile return.
8. Test two competing players, boats/passengers, death/dimension/logout/restart recovery, obstructed placement and modded machines.
9. Install the actual TaCZ/modpack and test headshots, AP, hitscan, explosive ammo and exceptional damage sources.
10. Sustain repeated maximum output: force and defense must remain stable indefinitely, with no fatigue or recovery.
