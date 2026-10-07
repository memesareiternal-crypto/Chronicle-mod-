# Architecture reference

[The root architecture document](../ARCHITECTURE.md) is authoritative. This index maps the reusable systems to source paths under `src/main/java/dev/chronicle/`.

| System | Source | Responsibility |
| --- | --- | --- |
| Contextual control | `power/Concentration.java`, `power/Intent.java` | Grip/group context, charge, modifiers, selection radius, pin/place, barrier and flight lifecycle |
| Progression/output | `power/Potential.java`, `Settings.java` | Persistent power, 10 stages, chosen output, strain, growth, strength and workload configuration |
| Shared physics | `power/Physics.java` | Composite mass/resistance, exclusive claims, reversible suspension, momentum, impact work and compression |
| Flight | `power/FlightControl.java`, `power/FlightGuard.java` | Acceleration/braking and narrow acknowledgement of authorized movement without creative permissions |
| Selection/utility | `power/Applications.java` | Server ray targeting, group acquisition, pressure, cuts, remote interaction, crops, deposit and construction |
| Queued world changes | `world/MassJobs.java`, `world/WorldActions.java`, `world/WorldAccess.java` | Loaded-world validation, reservations, recoverable transfers and shared per-dimension work budget |
| Grouped matter | `entity/MatterBody.java` | Palette states, relative cells, NBT, mass/hardness, ownership, transform and one ticking carrier |
| Protection | `power/Ward.java` | Sphere/dome/plane crossing, capacity, integrity, penetration, attachments and projectile capture |
| Networking | `network/Wire.java` | Bounded client intent; server view/effect/palette snapshot messages; inventory data remains server-side |
| Client presentation | `client/Presentation.java`, `client/PsychicGeometry.java`, `client/MatterRenderer.java`, `client/CrystalRenderer.java` | Meter above hotbar/XP, additive prismatic layer after translucent blocks with read-only depth, free camera, cached real block textures, animated crystal |
| Optional guns | `compat/GunBridge.java` | TaCZ reflection bridge and conservative ordinary Forge fallback |
| Awakening | `world/Chambers.java`, `world/CrystalSeed.java`, `entity/ResonantCrystal.java` | Budgeted rare chamber generation and persistent proximity-acquired power |
| Regression verification | `test/*Tests.java` | 38 integrated Forge GameTests; excluded from production jar |

No component exposes an ability selector. Moving structures do not create independently ticking cells. Output is one shared player setting; controls express physical operations through the existing target, force, and ownership systems.
