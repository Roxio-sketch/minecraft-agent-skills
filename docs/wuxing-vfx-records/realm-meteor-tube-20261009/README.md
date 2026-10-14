# Tube material GPU validation

This report is an independent WebGL2 check of the current production `realm_meteor` and `realm_meteor_post` vertex/fragment shaders. No production source was edited by this subtask. The original sphere report and its historical source/evidence remain separate.

The harness verifies that native and post fragments share the identical material function source, including `tubeTrail`. Both vertex programs use float `vec3` view normals. It binds the actual JSON `VisualTuning` defaults: pulse 0.08, turbulence 0.04 and flow 1.0.

## Synthetic geometry

A mildly curved 64-segment path has three nested, translucent, camera-independent radial meshes. Width uses the current 0.65 orb-radius ratio, smooth 0.88 power taper and faded root. The layer width multipliers are 1.08 / 1.0 / 0.42 and the layer alpha multipliers are 0.34 / 0.78 / 0.57. UV Y follows the production encoding `64 + (style - 1) * 6 + layer * 2 + theta / TAU`.

The five observer positions are side, direct rear, diagonal rear, direct front and above. The geometry uses 8 radial segments initially, then independently checks 6 and 4 at the same synthetic observer distances. It does not contain the actual player, sphere, world, Minecraft entity renderer, shader-pack renderer or shared screen bloom.

## Results

| Check | Result |
|---|---|
| Production native/post material source | Identical |
| Native/post GPU channel difference, all five views and three radial LODs | 0 |
| Circle seam cases: 5 styles × 3 layers × 3 times | 45 passed, maximum 1 channel unit |
| Time increment 0.001 s | Maximum 3 channel units; no pixel above the 3-unit change threshold |
| Side visible / blue pixels | 8,219 / 7,513 |
| Direct rear visible / blue pixels | 1,311 / 936 |
| Maximum side / rear screen thickness | 48 / 48 pixels |
| Rotation covariance of world geometry, normals and camera | Maximum 1 channel unit, no changed pixel above threshold |
| Full opaque foreground rejection in post shader | 0 visible pixels |
| Floating-point emission cases: 5 styles × 3 layers | 15 passed |
| Tube-only premultiplied emission scale | 0.45, maximum RGB error 1.63e-8 |
| Emission nearest-depth metadata error | 0 |
| Full opaque foreground emission rejection | 0 nonzero samples |
| WebGL error | 0 |

Each observer sees real transverse coverage rather than a single screen line. Blue pixels form 71–94% of the visible material at the tested settings. The current candidate reduces the outer/middle grazing density and white-edge gain and uses an angular alpha fade to soften long bright pipe edges. It additionally lowers the continuous middle density floor, lowers the core white/base terms and scales only tube emission by 0.45 to retain energy detail through bloom. The latest sparse material increases the three fibre wave amplitudes to 0.85 / 0.62 / 0.50 and multiplies their density by `0.08 + 0.92 * smoothstep(0.30, 0.72, fine)`. Middle density is `(0.04 + erosion² * 0.17 + rim * 0.025 + fibres * 0.48) * pulse`; core density is `(0.025 + erosion² * 0.23 + fibres * 0.42) * pulse`. The unblurred preview can still expose mild polygon edges and long angular highlights; this numerical PASS does not establish the desired natural appearance. Previous evidence remains under `pre-softedge-*`, `softedge-before-radiance-*` and `radiance-before-sparse-*`.

The latest locked fragment hashes are native `3ab62464536e3b761c39b2c9eafbd32d35ac86db01dfbaa4fcb789e00e08108b` and post `a1957746a47cb52ce17babc8717a1b85eb5f6ccf8354ca1852f948797e3e5dbb`. The shared material functions remain identical; the post-only emission output intentionally premultiplies RGB by alpha and 0.45 while storing unscaled linear depth in alpha. The RGBA32F check verifies both contracts directly on the production GLSL, with no blend hiding errors.

The six-panel screenshot compares day and night backgrounds for side/rear angles, plus diagonal and overhead views. It intentionally identifies itself as a synthetic GPU preview. Actual native/Oculus day/night, player readability, sphere-to-tail blending, transparency sorting, real path generation, stop/teleport cleanup and performance must be judged in the separate Minecraft client run.

Run `check-tube-material.cjs` with the bundled Node runtime and Playwright. `shader-validation.json` stores the exact shader source hashes, pixel metrics, LOD checks and all individual seam cases. `tube-material-harness.png` is the standalone preview.
