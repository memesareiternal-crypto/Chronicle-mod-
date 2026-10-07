# Codex handoff

Read this first. Actual repo: `Chronicle-mod` inside the Telekinesis workspace. Forge 1.20.1 / 47.4.26, Java 17, namespace `psychokinesis`, version `3.0.0-tatsumaki`.
Branch: `codex/chronicle-psychokinesis`; existing draft PR https://github.com/memesareiternal-crypto/Chronicle-mod-/pull/1 . Do not assume merged.

The user approved the Tatsumaki revamp and explicitly asked for simple controls, 2–3 dedicated keys, double-Space flight, direct personal damage resistance, automatic large/group manipulation, fixed crystal flicker and removal of ALL overuse mechanics. Preserve these requirements. No micro-selection of terrain sections or ability menus.

Controls: right click automatic grip/steer; left charge/throw or crouch disarm/crush; G automatic gather/persistent self orbit (sprint aims orbital center); V rapid/single/all launch or contextual force/piercing/radial release; B instant personal reinforcement and hold/release projected field. Double Space flight; WASD/Space/crouch/sprint maneuver. Public `/psychokinesis toggle` replaces the old chord.

Output: crouch-scroll globally changes 5%–100%, while hotbar scrolling ALSO continues. One unlabeled 182px prismatic strip above hotbar/XP. No camera control in production. Default maximum ~323 entities, 32,768 block cells, 24-block selection radius, ~167-block reach, shared 2,048 work steps/dimension/tick. New reach/flight/block/work keys replace older default names so stale values cannot preserve the weak previous behavior. Strength 0.1–20 and XP multiplier remain configurable.

Defense: direct damage-event reduction including generic mod-style attacks, passive configurable projectile reversal, orbiting real debris interception, finite projected sphere/dome/plane fields, explosion path suppression/containment. Precision uses magic damage, which bypasses armor while keeping resistance/counters.

Terrain: connected extraction, automatic cohesive debris fragments, force raising/flattening, impact craters/secondary debris, drilling/shearing, inventory-safe relocation. Original container data remains authoritative exactly once. Large collision uses a coarse center proxy; no arbitrary terrain tilt/twist.

Rendering: actual resource-pack block quads, client mesh pages limited to 2,048 cells/frame, GPU cache, client carrier interpolation. Prismatic surfaces use additive read-only-depth state. Crystal uses original intact model/textures; each quad draws once with full-bright animated vein material. Removed near-coplanar additive overlay.

Verified locally: production build, all 51 Forge GameTests, isolated client flight and isolated renderer smoke. Client flight actually pressed vanilla keyboard mappings through network input: forward 88.9 blocks, strafe 26.2, ascent 27.5, hover/braking, gravity restored, server position matched. No repeated motion-packet overwrites. Test helpers excluded from jar.

Tests include ordinary right-click 120-mob crowd and 1,728-block mass, independent 256 grips, unlimited-use state migration, instant B generic damage, double-Space gesture, orbiting block interception, no spurious burst after exhausted volley, flattening with preserved mass, impact/container inventory and projected blast suppression. TestConfigScope uses an in-memory config copy to avoid Forge file-watcher races on Linux.

Build: JAVA_HOME=`C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.20.101-hotspot`, GRADLE_USER_HOME=`C:\\Users\\memes\\.gradle`.
`gradlew.bat --offline build`, `gradlew.bat --offline runGameTestServer -PcleanGameTests`.
Jar: `build/libs/psychokinesis-3.0.0-tatsumaki.jar`.

Client audit: `run-flight-audit/saves/Flight Audit` is an isolated copy of the dev world. `-PflightAudit` exercises real keys and auto-exits; result is in `run-flight-audit/flight-audit-result.txt`. `-PvisualSmoke -PisolatedVisual` verifies render-only fixture and auto-exits. Ordinary `-Pplaytest` grants actual max power once and never supplies fake presentation/camera input. Do not run concurrent Gradle game sessions or overwrite user worlds. Never ship/enable test fixtures in production.

Outstanding external validation: live dedicated multiplayer, installed TaCZ/headshot/AP/hitscan/explosive ammo, scripted modded bosses, custom multiblock relocation and shader/resource-pack compatibility. Do not claim those were playtested. See README, ARCHITECTURE and docs/COMPATIBILITY.

Invariants: server chooses interactions, exclusive reversible ownership, inventory-safe recoverable transfer, bounded server/client workloads, no ability menu, no power hotbar items, no per-cell ticking entities, no overuse mechanic and no creative-flight permissions.
