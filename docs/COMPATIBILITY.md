# Compatibility and verification limits

The 2.0.0 engine passes 38 GameTests. External mods, resource packs, and live multiplayer still require their own acceptance pass; automated tests do not establish every mod combination.

## Rendering, matter, and world integration

- Prismatic surfaces draw after translucent blocks with additive blending and read-only depth. The films do not darken the scene or constrain the camera. Resource-pack and shader-mod translucency ordering still needs integration testing.
- Moving ordinary blocks use their actual baked resource-pack textures in cached GPU meshes. Animated block-entity-only models use static boxes textured with the model's particle sprite; custom chest/machine animations are not carried as live block-entity renderers. Their stored server NBT remains available for placement.
- Source water/lava cells use still-fluid textures and the same inventory-safe carrier representation. They are grid volumes rather than freeform fluid simulation.
- Carrier collision uses coarse bounds. Concave per-cell collision, elastic structural deformation, and material-dependent support collapse are not part of this build. The latest user request excludes arbitrary tilting and terrain twisting; rigid yaw quarter turns remain available.
- Standard block-state rotation is preserved. Modded block entities can store additional orientation, network, or multiblock relationships in their own NBT; those need mod-specific checks after relocation.
- Chunk availability, standard edit permissions, Forge block-break vetoes, and queued-cell reservations apply. Third-party claim systems and multiblock machine placement paths have not been comprehensively validated.
- Boss health, mass, armor, and passenger resistance participate in grips. Scripted boss motion, multipart hitboxes, and bespoke attacks can override ordinary movement; no boss-specific cinematic behavior is claimed.
- Radar uses vanilla glowing and can be visible to other clients.

## Projectiles and TaCZ

Vanilla projectile capture, barrier interception, redirection, and release use real entities. Unknown projectile implementations can supply a numeric `chronicle_damage` persistent estimate. Otherwise they retain impact-time Forge damage evaluation rather than receiving a guessed damage value.

The optional TaCZ adapter was checked against its official 1.20.1 source: [EntityKineticBullet](https://github.com/MCModderAnchor/TACZ/blob/1.20.1/src/main/java/com/tacz/guns/entity/EntityKineticBullet.java) exposes `getDamage(Vec3)`, and [EntityHurtByGunEvent](https://github.com/MCModderAnchor/TACZ/blob/1.20.1/src/main/java/com/tacz/guns/api/event/common/EntityHurtByGunEvent.java) exposes the pre-hurt event, attacker, base damage, and headshot multiplier. The bridge uses `getAttacker()` for directional protection and evaluates combined damage before armor-piercing splits. This verifies the integration API, not live TaCZ behavior. Different versions, hitscan paths, explosive/incendiary ammunition, and custom gun packs need installed-mod tests.

## Manual acceptance pass

1. Awaken near a crystal; check its animated emissive cracks and the prismatic effects under the intended resource pack. Toggle presentation options and concentration.
2. Crouch-scroll output down/up; verify the unlabeled meter, changed strength/range/group size, and simultaneous vanilla hotbar scrolling.
3. At stages 1, 2, 6, and 10, grip one/two/dozens/hundreds of mobs; move, add targets, disarm, compress, rotate, pin and throw. Co-moving captured groups must not damage each other.
4. Move loaded containers, trees, large terrain, a house and source fluids. Obstruct and retry placement; interrupt transfers and restart with an airborne carrier. Check every inventory and block count.
5. Use two players competing for the same mob, occupied boat, and projectile. Dismount, disconnect, die, and change dimensions while holding. Verify exclusive claims and restored state.
6. Sustain flight while carrying matter on a dedicated survival server with `allow-flight=false`; verify acceleration, braking, no kick, and unchanged creative permissions.
7. Test sphere/dome/plane barriers against weak and overwhelming projectiles, remote protection, captured releases, resizing and integrity failure. Repeat with installed TaCZ headshot/AP/hitscan/explosive ammunition.
8. Exercise crop tending, remote redstone, container deposits, mirrored construction and cutting gestures. Profile hundreds of moving targets and large mesh carriers under the actual modpack.
9. Sustain output above strain capacity at low and maximum stages. Control must weaken without direct overuse damage.
