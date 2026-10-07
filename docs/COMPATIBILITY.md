# Compatibility and verification limits

This is the first remake build, with a broad implementation of the requested physical kit. It needs interactive multiplayer and modpack playtesting before being treated as a finished release.

- Airborne structures use merged, flat material colors and box collision. Exact block textures, animated block-entity renderers, concave collision and arbitrary elastic deformation are not implemented. Terrain twists are rigid quarter-turn transformations. A structure that cannot settle stays recoverable rather than dropping blocked contents or deleting them. Structural-support simulation and automatic breakup into unsupported debris remain unimplemented.
- Liquid cells can be lifted, moved, rotated and placed through the same matter snapshots. They are block-grid volumes; freeform fluid surfaces and permanent independently shaped fluid stasis are not implemented.
- Quarter-turn placement rotates standard block states. Some modded block entities store additional orientation/link data in their own NBT; those integrations need individual verification. Containers remain subject to the configurable carry-data policy.
- Forge block-break vetoes and standard edit permissions are consulted. Custom placement paths in third-party claim mods and multiblock machines need testing. No general claim-mod compatibility guarantee is made.
- Vanilla arrow/trident/fireball/skull/snowball/egg interception is implemented. Other projectile mods can provide a conservative `chronicle_damage` persistent numeric estimate. Without one, unknown projectiles are evaluated at impact instead of being guessed from size or speed.
- The optional TaCZ bridge targets `com.tacz.guns.entity.EntityKineticBullet#getDamage(Vec3)` and `EntityHurtByGunEvent.Pre`. It checks total headshot-adjusted damage before split damage events. This API was checked against the official 1.20.1 source, but TaCZ has not been installed and playtested in this workspace. Different TaCZ versions, explosive/incendiary ammunition and custom gun packs may need adaptations.
- Bosses use the general health/mass resistance and projectile rules. Their special AI, multipart hitboxes and scripted attacks can override ordinary motion. Bespoke Warden attention control, Dragon multipart behavior and boss-specific cinematics are not implemented.
- Radar currently uses vanilla glowing and is visible to other clients. A viewer-private sensing renderer is future work.
- No custom ability UI or power particles are added. Hints use the existing action bar; the aura and selection/placement previews use line geometry. Ordinary Minecraft combat or transformed blocks can still emit their vanilla effects.

## Manual acceptance pass

1. Awaken near a spawned crystal, toggle concentration, and verify normal hotbar scrolling with neither power key held.
2. Grab, rotate, throw and place blocks, a loaded chest, source water/lava, a tree and a house. Obstruct placement and retry. Restart with a body airborne.
3. Throw zombies through other mobs and into walls; manipulate a boat containing a rider; test two players competing for the same target. Disconnect, die and cross dimensions while holding matter.
4. Compare weak and strong incoming arrows and TaCZ bullets. Verify stasis, returning shots, field breakage, projected protection and cleanup.
5. Exercise every cutting shape, crop replanting, mirrored building and remote redstone. Verify inventories and item counts.
6. At level 1 and maximum level, sustain expensive uses beyond the threshold. Control must degrade while the player's health remains unchanged.
7. Check platform travel, flight permission restoration, dedicated-server loading and client rendering under resource packs.
