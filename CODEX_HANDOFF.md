# Codex handoff

Current branch: `codex/chronicle-psychokinesis`. PR: https://github.com/memesareiternal-crypto/Chronicle-mod-/pull/1

Works: context grip/force controls, right-click activation, dedicated scalable barriers, 10-stage progression and public level command, one-entity block masses with real block textures, strain without self-damage, geometric effects toggle, corrected crystal UV/full-bright conversion, Forge build, 9 GameTests.

Partial: flight still uses vanilla ability movement; large mass extraction is synchronous and bounded; barrier planes use the player's current facing for protection checks; cutting exposes one contextual sweep; fluid movement and client mesh caching are not implemented.

Current bugs/risks: visually verify the corrected OBJ crystal in a fresh client session; stress-test thousands of surface cells; multiplayer input and modded gun integration need live testing.

Next tasks:
1. Replace creative-style flight with acceleration steering.
2. Queue large world extraction/placement across tick budgets.
3. Cache block-model mass geometry into grouped render buffers.
4. Expand gesture-based cut geometry without modes.
5. Add group entity acquisition and pin/anchor gestures.
6. Add barrier impact sound and directional deformation.
7. Stress-test large masses and multiplayer.

Core paths: `power/Concentration.java`, `power/Physics.java`, `power/Applications.java`, `entity/MatterBody.java`, `power/Ward.java`, `client/Presentation.java`, `client/MatterRenderer.java`, `Settings.java`.

Build: set Java 17, then `gradlew.bat build` and `gradlew.bat runGameTestServer`.

Do not restore a mode selector, action bar mode names, per-block entities, particle spam, or overuse damage.
