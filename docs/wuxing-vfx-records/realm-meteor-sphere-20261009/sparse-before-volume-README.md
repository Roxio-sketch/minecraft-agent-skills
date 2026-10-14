# Higher-realm sphere material

Production files changed by the shader subtask:

- `realm_head_energy.fsh`: replace the former long advanced filament field with an analytic unit sphere. Near and far surfaces use independent opaque-depth rejection, low blue central coverage, artistic two-sided Fresnel, a forward highlight, and three irregular moving surface streams with narrow white-blue cores and wider cyan skirts. Two of those streams form short attached bends on the forward hemisphere. Rear rim emission/RGB dim independently so the intact transparent body can merge into the tail.
- `MeteorHeadRenderer.java`: project a tighter ±1.02 proxy for higher-realm spheres. Preserve the original Yuanying box and existing create overload; add a speed/turn overload and per-head sanitised motion fields.
- `realm_head_energy.json`: register the neutral-default `FlightMotion` vec2 uniform.

The host supplies a physical orb radius and optional bounded longitudinal/transverse stretch, anchored at the original player's interpolated body centre. Inverse-transpose view normals preserve the Fresnel under nonuniform scale. The analytic silhouette does not depend on raymarch sample count, arm animation, roll or tail geometry. `FlightStyle == 0` retains the legacy density and integrator. This subtask does not modify movement, collision, first-person camera, player model visibility, shared bloom state or the trail.

## Latest material parameters

| Parameter | Value |
|---|---:|
| Unoccluded central opacity before forward highlight/streams | 0.077596 |
| Grazing rim opacity increments: near / far surface | 0.240 / 0.155, before variation/rear modulation |
| Fresnel power | 1.6 |
| Maximum rim base RGB gain | 1.22 |
| Surface stream count | 3 |
| Minimum irregular stream continuity | 0.72 |
| Stream opacity: cyan skirt / narrow core | 0.065 / 0.310 |
| Far stream opacity ratio | 0.20 |
| Core direct RGB gain beyond body | 1.32 plus 0.24 forward |
| Stream core emission gain | 4.80 plus 0.90 forward |
| Rear hemisphere blending factor | 0.78 |
| Rear rim-only RGB/emission multiplier | 0.30 |
| Rear rim-only opacity multiplier | 0.55 |
| Turn-only stream-coordinate offset | 0.15 |
| Speed stretch of advected coordinate | 1.0 to 1.85 |
| Rim emission gain | 1.10 |
| Narrow core irregular segmentation floor / range | 0.20 / 0.80 |
| Forward bend angle harmonics A / B | 2 / 3 (integer periodicity) |
| Stable rim-only RGB/emission variation | 0.35 to 1.00 |
| Rim opacity variation derived from radiance | `mix(0.20, 1.0, radianceVariation)`, resulting range 0.48 to 1.00 |

The first and second native runtime captures were visually rejected: round geometry and the readable original player/armor were established, but the head retained a bubble appearance and its surface streams remained too faint. Phase 1.3 uses actual narrow luminous stream cores, softer cyan skirts and independent rear rim fading. Earlier source and numerical/GPU evidence remain under `first-*` and `second-*` filenames. Mathematical/GPU PASS does not mean a runtime visual PASS.

The current candidate additionally reduces continuous rim RGB/emission, keeps cyan skirts continuous while breaking the narrow white-blue cores into short irregular pieces, and uses integer 2/3 forward-cap angle harmonics to close the angular seam. Previous phase 1.3 GPU captures and metrics remain under `pre-softedge-*`. The latest `shader-validation.json` and `motion-geometry-validation.json` record the new source SHA-256; they do not inherit a prior runtime visual verdict.

The latest locked material candidate adds stable noise to rim RGB/emission and uses that variation to reduce the rim-only opacity, while retaining the low central base coverage. It strengthens the short narrow cores and reduces far-surface stream coverage to 0.20. Its shader hash is `ed49888694a19077f50c07b28683516e3ac6b965f620c9f10d86c8799e0e176d`. Preceding evidence remains under `softedge-before-radiance-*` and `radiance-before-sparse-*`. Latest GPU output has central alpha 0.07843, central body-depth alpha 0.05098 and sampled rim alpha 0.30196 at radius 90; normal/stretched foreground rejection is complete. Legacy output and 8/24 sample-count comparisons both have zero pixel delta. Animation, speed and opposing turn alter 13,563 / 15,195 / 11,502 pixels respectively, with WebGL error 0. Actual game visual acceptance remains separate.

## Verification

`sphere_geometry_regression.py` and the historical `geometry-validation.json` describe the first/second round-sphere versions. Phase 1.3 adds `motion_geometry_regression.py` and `motion-geometry-validation.json`: independent ellipsoid intersections, ray-distance conversion, inverse-transpose normal correctness, surface depth rejection and bounded phase changes under changing speed. Speed changes scale only the spatial advected coordinate; they never multiply elapsed world time by a changing speed.

The latest motion regression passes all 612 ellipsoid rays, with maximum surface residual 8.22e-15 and inverse-transpose normal angular error 2.58e-8 radians. Its source hash matches the latest GPU report and the production shader.

`check-sphere-material.cjs` compiles the actual production vertex/fragment shaders in WebGL2, with only version/precision and the GLSL 150 `sample` variable name adapted for GLSL ES 300. It checks circular bounds, transparent centre, brighter rim, animated surface streams, full foreground rejection, independent body-depth clipping, emission presence, sample-count independence, and identical Yuanying GPU output to the historical unchanged legacy branch. It additionally checks speed and opposing turn feedback, bounded 1.12/0.97 stretch and occlusion of that stretched head. Its six panels use a day sky, night sky, synthetic body-depth mask, foreground block depth, high-speed stretch and turn feedback. These panels are synthetic GPU renders, not Minecraft screenshots.

The targeted Java 17 compile passed. The existing mapped classpath emits one pre-existing `ResourceLocation(String, String)` deprecation/removal warning.

Unified Forge builds, native/Oculus day/night captures and final installation are owned and recorded by the parent task. Numerical checks and the synthetic material harness do not replace actual runtime visual approval.
