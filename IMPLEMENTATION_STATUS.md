# Implementation status — 2.0.0-rebuild

| Area | State | Current behavior / verification |
| --- | --- | --- |
| Contextual controls | DONE | Primary right-click grip; optional G; left-click throw/disarm/compression; dedicated B; no mode selector |
| Output adjustment | DONE | Global crouch-scroll, 5%–100%, unlabeled meter aligned above hotbar/experience; hotbar scrolling continues |
| Entity/group manipulation | DONE | 1/2/dozens/hundreds progression; 256 actual mobs gripped, steered, and released in tests |
| Composite ownership | DONE | Vehicle/passenger mass and resistance; exclusivity, dismount cleanup, topology checks |
| Force/collision/compression | DONE | Shared momentum, swept impacts, blocked-axis wall damage, work-based compression, group self-collision protection |
| Blocks/structures | DONE | Connected extraction, one palette carrier, quarter-turn rotation, throws, precise queued deployment |
| Large world edits | DONE | Reserved cells, chunk checks, recoverable transfers, shared per-dimension budget |
| Block textures | DONE | Cached GPU meshes of real baked models; fluid texture and block-entity static fallback |
| Projectile manipulation | DONE | Reversible capture/steering/redirection; barrier interception and arrow damage safety |
| Barriers | DONE | Sphere/dome/plane, remote positioning, resizing, charge/output, integrity, penetration and failure |
| Effects/crystal | DONE | Additive prismatic surfaces/ripples after translucent blocks, read-only depth, free camera, optional presentation, animated emissive crystal cracks |
| Flight | DONE | Acceleration, braking, movement modifiers, no creative permission grant, guarded floating timeout |
| Precision/utility | DONE | Remote redstone, crop tending, item deposit, cutting gestures, mirrored construction, surface pinning |
| Progression/config/commands | DONE | Public level out of 10; admin tools; growth/output/strength/scale settings; persistent power |
| Overuse | DONE | Strain lowers control without direct self-damage |
| Regression tests | DONE | All 38 Forge GameTests pass; production build packages the final renderer and meter changes |
| External integration validation | UNVERIFIED | TaCZ API checked against official source; live TaCZ/modpack and multiplayer acceptance still need playtesting |

Practical limits: coarse carrier collision, static fallback for custom animated block-entity renderers, grid-based fluids, and possible boss-script motion overrides. The latest request excludes arbitrary tilting and terrain twisting. See [compatibility](docs/COMPATIBILITY.md) for the remaining verification matrix.
