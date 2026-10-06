# Continuation — Chronicle remake

## User intent

The user explicitly corrected the initial approach: **a remake, not a migration**. Keep all gameplay in the new `dev.chronicle` architecture. Retain only supplied crystal artwork and Forge tooling from the previous project. Target GitHub repository: `memesareiternal-crypto/Chronicle-mod-`, base `main`, working branch `codex/chronicle-psychokinesis`.

## Current implementation

Forge 1.20.1 / 47.4.26, Java 17, version `1.0.0-remake`, registry namespace `psychokinesis`. Fresh modules implement potential/strain, bounded two-key intent, shared physical holds, projectiles/collisions, block and structure snapshots, region/terrain, eight cutting geometries, remote building/interactions, crop replanting, anchored fields, flight, regeneration, sensing, pressure/explosion, crystal entities/chambers and commands. The client batches a cached greedy exterior mesh for matter. No gameplay Java remains from `com.telekinesis`.

## Verification and immediate work

- Production `build` passes. Jar: `build/libs/psychokinesis-1.0.0-remake.jar`.
- Actual Forge GameTest run discovered and passed all 9 required tests. Do not confuse earlier fixture setup failures/zero-test runs with the final passing run.
- The production jar was inspected: zero legacy `com/telekinesis` classes and zero bundled test classes.
- Client launched, loaded assets without mod/resource errors, and joined a newly created local world. The user then stopped Computer Use with Escape. Do not resume UI automation without a new request. Full interactive gameplay/modpack validation is not complete.
- Published draft PR: https://github.com/memesareiternal-crypto/Chronicle-mod-/pull/1 . The branch contains the remake; main has not been merged. Follow-up work is the acceptance checklist and fidelity limits in docs/COMPATIBILITY.md, not rebuilding the project.
- `docs/COMPATIBILITY.md` is the authoritative list of fidelity limits and unverified behavior; do not claim every requested nuance is finished.
## Build commands on this workstation

Set `JAVA_HOME` to the installed Java 17 directory (`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`) and `GRADLE_USER_HOME` to the existing user cache (`C:\Users\memes\.gradle`), then run `gradlew.bat --offline build` and `gradlew.bat --offline runGameTestServer`. Network/sandbox escalation may be required to access the cache. Logs/build/run directories are ignored; do not commit them.

## Design invariants

- Exactly two custom keys; vanilla modifiers and wheel supply intent. Normal hotbar scrolling must work without a power key held.
- Server chooses targets and computes force/damage. Packets never provide world edits or arbitrary coordinates.
- Ownership is exclusive; every suspension restores AI/gravity on cleanup. A thrown target must not be immediately re-grabbed until G is released.
- Snapshot capture and placement preserve inventories and validate before mutation. Failed placement must leave every cell recoverable.
- All levels have finite strain capacity. Sustained flight/fields/holds must not be offset by idle recovery in the same tick.
- Never emit power particles or call vanilla explosion/destruction effects to implement psychic visuals.
- Only crystal assets and the build scaffold are inherited. Do not resume the old implementation in the parent directory.
