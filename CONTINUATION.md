# Continuation — Chronicle rebuild

Read [CODEX_HANDOFF.md](CODEX_HANDOFF.md) first; [ARCHITECTURE.md](ARCHITECTURE.md) and [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) describe the current systems. [Compatibility](docs/COMPATIBILITY.md) separates implemented behavior from external playtesting.

The user requested a substantial remake matching the attached psychokinesis specification, then refined controls and presentation. Right click now manipulates directly, G is an optional alternate/add-target grip, B shapes protection, and crouch-scroll sets output while preserving hotbar scrolling. The latest request permits one unlabeled output meter and excludes arbitrary tilt/twist features. Smooth prismatic surfaces replace wire effects; the crystal uses its valid supplied artwork plus animated emissive cracks.

All gameplay is under `dev.chronicle`; do not resume the old `com.telekinesis` implementation in the parent directory. Forge 1.20.1 / 47.4.26 and Java 17 produce `psychokinesis-2.0.0-rebuild.jar`. The GitHub destination is `memesareiternal-crypto/Chronicle-mod-`, branch `codex/chronicle-psychokinesis`, PR #1. The working branch is distinct from `main` until the PR is merged.

The final production build and 38 real Forge GameTests pass. Tests include 256-mob group selection and reversible control, output adjustments, the skeleton disarming input path, composite claims, collision damage, barrier/projectile interaction, queued inventory-safe extraction/placement, and flight timeout/permission safety. Test classes are excluded from the production jar. Client screenshots verified the latest prismatic films, animated crystal emission, textured mass, and meter above the hotbar. The test camera lock was removed. Live multiplayer and TaCZ modpack validation remain separate acceptance tasks.

For this workstation, set `JAVA_HOME` to `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` and `GRADLE_USER_HOME` to `C:\Users\memes\.gradle`, then run `gradlew.bat --offline build` and `gradlew.bat --offline runGameTestServer`. Cache access can require sandbox escalation. Do not run concurrent Gradle game sessions or commit ignored build/run/log artifacts.

Preserve server-authoritative input, exclusive reversible ownership, inventory-safe recoverable transfers, cached block-texture meshes, global work budgets, finite strain without self-damage, and acceleration flight without creative permissions. Extend physical primitives and contextual gestures rather than exposing a list of selectable powers.
