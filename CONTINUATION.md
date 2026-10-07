# Continuation — Tatsumaki revamp

Read [CODEX_HANDOFF.md](CODEX_HANDOFF.md), [README](README.md), [architecture](ARCHITECTURE.md), [status](IMPLEMENTATION_STATUS.md) and [compatibility](docs/COMPATIBILITY.md).

The user approved the revamp in the attachment dated October 7, 2026. Keep controls simple: G/V/B plus contextual mouse input, automatic group/terrain scale, double-Space flight, direct personal resistance, and absolutely no overuse mechanics.

All gameplay belongs under `dev.chronicle` in the nested `Chronicle-mod` repository. Do not revive the legacy `com.telekinesis` implementation in the parent folder. The GitHub destination is `memesareiternal-crypto/Chronicle-mod-`, branch `codex/chronicle-psychokinesis`, draft PR #1. Version: `3.0.0-tatsumaki`.

Production build and 51 Minecraft tests pass locally. Real keyboard/network client flight and isolated visual smoke pass. Generic mod-style damage resistance is tested; live TaCZ/multiplayer/modpack acceptance remains separate. Tests and fixtures are excluded from the production jar.

Extend shared forces and physical matter. Preserve server authority, exclusive reversible claims, inventory-safe transfer, actual block textures, bounded mesh/edit work, the unlabeled output meter, normal hotbar wheel behavior and free camera control. Terrain tilting/twisting is excluded.
