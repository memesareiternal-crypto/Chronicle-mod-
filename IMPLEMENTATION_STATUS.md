# Implementation status — 3.0.0-tatsumaki

| Area | Implemented behavior and evidence |
| --- | --- |
| Simple controls | Three dedicated keys G/V/B, contextual mouse input, automatic group/terrain selection; no section-selection workflow |
| Output | Crouch-scroll, unlabeled meter above hotbar/XP, ordinary hotbar scrolling retained |
| Group control | One/two/dozens/hundreds scaling; 120 mobs through ordinary right click and 256 through the grip solver tested |
| Huge terrain | Default level-10 budget 32,768 cells; 1,728-cell automatic right-click lift tested |
| Debris | Automatic cohesive gathering, persistent self/point orbit, single/rapid/all launches; real block-arrow interception tested |
| Destruction | Momentum impacts, knockback, shockwaves, secondary preserved terrain, cavities, ground raising/flattening, drilling/shearing |
| Inventory safety | Queued extraction/placement, reservations, interruption/restart recovery, seven-diamond impact/container tests |
| Flight | Double Space; actual client keyboard/network movement, strafe/ascent/braking and gravity restoration verified |
| Personal defense | Instant B damage resistance, output/strength scaling, optional passive projectile reversal, generic mod-style damage test |
| Projected fields | Sphere/dome/plane, charge/resize, finite integrity, projectile capture/return, explosion-path suppression tested |
| Crystal/effects | Single-pass animated vein emission, original intact artwork, smooth prismatic films; two client glow screenshots inspected |
| Performance | Shared server edit budget, bounded client mesh pages, cached GPU textures, coarse large-body collision proxy |
| Progression | Ten stages, public level/controls/toggle commands, XP rate multiplier and 0.1–20 strength |
| Repeated use | No exertion, stamina, damage, recovery or control degradation; legacy state/config purged |
| Validation | Production build and all 51 GameTests pass locally; isolated actual client flight and renderer smoke pass |
| External acceptance | Dedicated multiplayer, live TaCZ/modpack, shader/resource-pack combinations remain unverified |

No arbitrary terrain twisting or tilting. Carrier collision is approximate; custom animated block entities use static textured fallback; boss scripts/multipart models may override movement. See [compatibility](docs/COMPATIBILITY.md).
