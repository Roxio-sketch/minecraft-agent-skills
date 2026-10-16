# Procedural space rift modules

Pure procedural slit/tear geometry, animated galaxy interiors and layered blue-violet rims. Fourteen JSON presets share one geometry and shader pipeline. No rift PNG is used. This source package excludes the private integrated workbench and its launcher.

## Use

Requires Python 3.12+ and Node.js. Run from this directory:

```
python scripts/build.py
node scripts/check_geometry.mjs
```

The builder creates WebGL2 `shader/rift.frag`, a Shadertoy-compatible `shader/shadertoy.glsl`, and Forge GLSL150 resources. For an existing preview, import `shader/parameters.js` and `shader/geometry.js`, upload their uniforms and compile the generated shader. SceneColor must be populated by the host. The Shadertoy example supplies its own test background; it is not real scene refraction.

Geometry and parameters are separate modules. Presets under `presets/` expose geometry, color, rim depth, distortion, lifecycle and quality. The three `horizon-*` presets are clean horizontal galaxy rifts; the other presets retain alternate algorithm styles. Physical local coordinates and derivative filtering preserve procedural detail when changing length and zoom; details remain limited by pixel resolution and the selected quality budget.

## Forge 1.20.1

`forge/` contains a Java 17 example. Run `gradlew build` there after generating resources. Inspect its client renderer and public rift API before integration. Position/orientation and uniforms are runtime inputs. Copy scene color once for the pass, preserve depth testing, and release/recreate render targets on resize. Replace `shader/interior-adapter.glsl` to reuse a sky or another interior renderer, then regenerate all targets.

Optional desktop shader/depth verification: install moderngl, glfw, numpy and Pillow, then run `python scripts/verify_desktop_gl.py`. Reports stay in ignored `evidence/`. Desktop GL tests and compilation do not certify Minecraft runtime behavior, third-party shader compatibility, or a traversable second world.

See NOTICE.md for provenance and licensing boundaries.
