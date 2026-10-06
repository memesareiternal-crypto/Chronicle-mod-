# Architecture of the remake

All gameplay was rewritten under `dev.chronicle`. The original `com.telekinesis` implementation is absent.

- `Potential`: persistent acquisition, active state, random color, quadratic experience progression and strain. Age experience is earned only while online. An inactive power retains its level.
- `Intent` and `Wire`: clients submit bounded button bits and wheel direction. They never submit targets, positions, force or damage. A direction-restricted channel carries server state back to clients.
- `Concentration`: one transient server session per player. Owns the intent dial, continuous holds, selection, field, flight, charges and modifiers. Timeouts release stale input. Logout, death, dimension changes and server shutdown restore controlled objects and flight permissions.
- `Physics`: exclusive entity ownership, reversible AI/gravity suspension, health/mass resistance, velocity changes, specialized item projectiles and swept collision damage. Entity NBT marks suspended state so reload can recover orphaned holds.
- `Applications`: server ray targeting, connected-tree lifting, region peeling, pressure, remote interaction/building, seed-accounted farming and geometric cuts. Block edits respect loaded chunks, world borders, player edit permissions and Forge break-event vetoes.
- `MatterBody`: one entity stores many block states and optional block-entity NBT. Capture validates the complete set before changing it; removing block entities before replacement prevents container duplication. Deployment validates all destinations before writing. Body orientation rotates relative positions and states together. Inventory data never travels in the client mesh packet.
- `Ward`: follows self, an entity or an anchor. Its finite integrity, pressure boundary, projectile collection and expendable orbiting items share the same ownership layer as grabs.
- `GunBridge`: optional reflective integration with TaCZ's public bullet and pre-hurt event API. Total damage is checked before AP/non-AP splitting. Unknown versions retain the Forge fallback.
- `Chambers` and `ResonantCrystal`: delayed, bounded chamber generation in new Overworld chunks plus a persistent oversized crystal entity. A testing egg spawns the entity only.
- `Presentation`: two key mappings, state-driven hints and geometry outlines. `MatterRenderer` greedily merges visible adjacent faces once and batches the resulting mesh. `CrystalRenderer` uses the supplied OBJ materials.

Do not introduce one binding per application. Extend the shared physical primitives or contextual interpretations. Never send particle packets or vanilla explosion/block-destruction effects from power code. Vanilla combat, item, fluid and block mechanics can still have their own normal effects.

Regression tests run in an actual Forge GameTest server. `tools/GenerateFixture.java` generates the bundled empty fixture using plain NBT. Production jar packaging excludes the test classes.
