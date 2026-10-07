# Codex handoff

Read this first. Forge 1.20.1 / 47.4.26, Java 17, namespace `psychokinesis`, version `2.0.0-rebuild`.
Branch: `codex/chronicle-psychokinesis`; PR: https://github.com/memesareiternal-crypto/Chronicle-mod-/pull/1 . Do not assume merged.

Verified: final production build and all 38 Forge GameTests pass, including 256 real held mobs, output scaling, disarming input, composite ownership, impacts, queued inventory-safe masses, barrier capture, and survival flight cleanup.
Works: primary right-click grip, optional G/add target, left-click throws/disarm/compression, pin/place, B sphere/dome/plane, contextual pressure/cuts/utility, acceleration flight, progression/config/commands.

Output: crouch-scroll globally sets 5%–100%; unlabeled182px meter above hotbar/XP (H-52survival/H-35creative); vanilla hotbar scrolling also continues. Output scales force/reach/mass/group/flight/barrier/healing/defense.
Visuals: more visible prismatic films/ripples, additive read-only-depth layer after translucent blocks; free player camera; no power particles/wire effects. Crystal has corrected textures and animated emissive cracks. Client screenshots verified the film, textures, glow pass, and aligned output meter. Test camera locking was removed.

Scale: default max-level target capacity ~323, ceiling512; block budget12288, volume32768; shared dimension work budget256. Existing user configs retain saved limits.
Matter: one palette carrier, cached actual block-texture GPU meshes, queued extraction/placement, NBT kept server-side. Source fluids are grid cells; animated block-entity-only renderers use static particle-texture boxes.
Limits: coarse bounds collision; some boss scripts override motion; live TaCZ and multiplayer/modpack acceptance unverified. Latest user explicitly excludes tilting/twisting; do not add them.

Next verification tasks:

1. Confirm GitHub CI and review the published rebuild before merging.
2. Run the full manual control sequence at stage1 and stage10, including crowded grabs and disarming.
3. Validate two-player competition, dimension/death/logout recovery, and survival flight on allow-flight=false dedicated server.
4. Install the intended TaCZ version and test weak/strong/headshot/AP/hitscan and explosive ammunition.
5. Profile large carriers and hundreds of moving targets under real resource packs/modpacks.

Core: `power/Concentration`, `Physics`, `Potential`, `Ward`, `FlightControl`, `FlightGuard`; `world/MassJobs`, `WorldActions`; `entity/MatterBody`; `client/Presentation`, `MatterRenderer`, `PsychicGeometry`; `Settings`, `network/Wire`, `compat/GunBridge`.
Build: JAVA_HOME=`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`, GRADLE_USER_HOME=`C:\Users\memes\.gradle`; `gradlew.bat --offline build` / `gradlew.bat --offline runGameTestServer`. All38 also pass with `-PcleanGameTests` (fresh config/world). Temporary test overrides use TestConfigScope (in-memory deep copy) to avoid Forge file-watcher races; compression tests isolate strain separately.
Jar: `build/libs/psychokinesis-2.0.0-rebuild.jar`. Keep run/build/log files out of commits.

Invariants: server chooses targets/edits; exclusive reversible claims; recoverable inventories; global tick budgets; no ability wheel/text mode labels, per-cell entities, self-damage from overuse, creative-flight permissions, or power particle packets.
