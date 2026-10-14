# Black Hole Material Lab

Original client-side Forge 1.20.1 / Java 17 rendering example. The world pass
combines scene lensing, a differentially rotating granular disk, radial texture
inflow, independent infalling dust and three local glow layers.

## Build

```powershell
python scripts/generate_resources.py
./gradlew.bat --console plain build
```

The packaged template includes `scripts/black-hole.glsl`. In the research
workspace the generator also finds the adjacent skill's preview kernel. An
explicit `--kernel <path>` overrides both. Generated resources are committed to
the template, so Python is needed only when regenerating shaders.

The build uses Gradle 8.8, ForgeGradle 6.0.54 and Forge 47.4.10. Downloading
dependencies requires network access on first use; cached builds can use
`--offline`. Install the reobfuscated `build/libs/black-hole-lab-0.1.0.jar` into
the selected Forge 1.20.1 client and restart after replacing a JAR.

## Commands

- `/blackhole world`: place the local effect 14 blocks along the view.
- `/blackhole off`: remove it.
- `/blackhole size 1.6`: world scale.
- `/blackhole color 7D3CFF`: core color; `000000` restores black.
- `/blackhole lens true`: current-scene lensing.
- `/blackhole lens_strength 0.7`: lens strength (0–1, default 0.7).
- `/blackhole lens_radius 2.4`: independent lens radius (0.65–4.35 local units; multiply by `/blackhole size` for blocks).
- `/blackhole rotation 0.8`: independent disk angular speed.
- `/blackhole infall 0.22`: independent disk radial texture transport.
- `/blackhole particles true`: independent particle pass.
- `/blackhole particle_speed 1`: particle orbit/lifetime speed.
- `/blackhole halo 1`: total glow strength.
- `/blackhole showcase`: internal material and item samples.
- `/blackhole items`: native item comparison panel.

For baked items, `BlackHoleMaterial:{CoreColor:0}` enables a black-core internal
material. `Mask:"blade"` is only a vanilla diamond-sword color-mask example.
World particles/glow are not automatically included inside item materials.

## Source Map

`BlackHoleWorld` copies current scene color/depth and composites the ray field.
`BlackHoleParticles` owns static particle identities and the additive pass.
`scripts/disk_structure.glsl` controls granular bands and disk transport.
`scripts/particles.vsh` controls clustered spawning and integrated orbits.
`scripts/world_main.glsl` defines ring, disk and aura emission.
`BlackHoleRender` and `BlackHoleModels` implement shader registration and the
tagged baked-item path. Regenerate resources after editing source GLSL.

The local `black-hole-lab/command.json` developer bridge accepts one request at
a time. It is not a public recipe format. The optional `/blackhole verify`
audit changes player state temporarily and is restricted in source to the
authorized local ASTRA_VFX_TEST save; do not remove that guard without choosing
a new disposable test save. It records restoration checks and a recovery NBT.

## Verification and Limits

Forge 47.4.10 / RTX 4060 Ti: final world suite passed 28 checks across 35 native
captures, including depth, foreground/core occlusion and resource reload.
GPU motion probes separately verify disk transport and stable particle IDs:

```powershell
node scripts/validate_disk_motion.cjs --browser "C:/path/to/chrome.exe" --output evidence/disk
node scripts/validate_particle_motion.cjs --browser "C:/path/to/chrome.exe" --output evidence/particles
```

These optional probes require Playwright and a Chromium browser. They execute
the actual shader functions but do not replace Minecraft runtime verification.

This is artistic rendering, not scientific GR or fluid simulation. Scene
lensing cannot recover offscreen/hidden geometry. Particles use straight
projected centers with black-hole extinction, without multiple gravitational
images. Glow is local volumetric emission, not full-screen bloom. Entity/block
attraction, multiplayer synchronization, shaderpack compatibility, multiple
black holes, custom item renderers and worn armor need further integration.

Reference archives informed methods and appearance; no third-party mod code,
reference shaders, cubemaps or textures are bundled. The Gradle wrapper retains
its upstream notices.
