# Black Hole Material Lab

Standalone client-side Forge 1.20.1 / Java 17 example under dev.blackholelab.
The world compositor includes five disk styles, core/disk/layered-halo gradients,
optional polar jets, stable dust identities, a 360-degree angular environment
lens and whole-actor foreground restoration. The native baked-item/panel path
retains its original internal field and mask/override/fog/foil behavior.

## Build

Copy this source project into a work directory. Run:

```powershell
python scripts/generate_resources.py
python scripts/audit_resources.py
./gradlew.bat --console plain blackHoleCheck build
```

Gradle 8.8 / ForgeGradle 6.0.54 / Forge 47.4.10. First build needs downloads;
cached builds may use --offline. Install reobfuscated build/libs/black-hole-lab-0.1.0.jar
into the selected Forge client and restart after replacement. Keep build/.gradle/run
outputs outside the reusable skill asset.

--kernel changes only the old item/panel field. World/particle source is bundled
as world-kernel.glsl, world-layers.js, world-*.glsl, world_header/world_main,
particles_header/main and mature-*.json/vsh. Generated shaders are committed;
the audit verifies exact regeneration.

## Commands and Recipes

/blackhole world raycasts the configured view segment. A block hit offsets the
center by core radius plus .05 blocks along its normal; no hit uses the far point.
The center stays fixed afterwards. /blackhole off releases resources.

size, color, lens, lens_strength, lens_radius, rotation, infall, particles,
particle_speed, halo, showcase, items retain the original command interfaces.
/blackhole tilt 0 selects a horizontal disk (default); explicit imported angles
are preserved. /blackhole lens_players true opts players into lensing regardless
of mass-plane classification; false protects the complete models (default).

/blackhole import reads config/blackholelab/recipe.json. It supports preview/v1,
preview/v2 and black-hole-world/v1, validates before applying, preserves explicit
time/playing and maps viewDistance * worldScale to placement distance.
/blackhole export writes typed black-hole-world/v1. The bundled workbench accepts
this canonical bridge, preserves validated world-only fields through edits and
preset changes, and exports world/v1 directly back to Forge. Preview v1/v2
retain strict unknown-field rejection. Browser-only background/camera/HDR bloom and particle
midpoint tint are retained as metadata, not claimed to affect the world.

The legacy black-hole-lab/command.json bridge remains available for single-user
local development. The /blackhole verify item audit remains guarded to its
original authorized ASTRA_VFX_TEST save. Do not remove that guard to run on an
unselected user save.

## Verification

```powershell
./gradlew.bat --offline --console plain runClient -PblackHoleGroundRegression=true
python scripts/accept_ground_preview.py --source run/black-hole-lab/world-probes --out evidence/ground
node scripts/validate_lens_geometry.cjs --output evidence/lens-math.json
node scripts/check_shader_interfaces.cjs --browser "C:/path/to/chrome.exe" --output evidence/interfaces.json
node scripts/validate_disk_motion.cjs --browser "C:/path/to/chrome.exe" --output evidence/disk
node scripts/validate_particle_motion.cjs --browser "C:/path/to/chrome.exe" --output evidence/particles
```

Ground flag defaults false. It opens a disposable BlackHoleLab-Ground-Placement-Check,
skips first-run onboarding only in this fixture, performs one actual downward
placement, captures six viewpoints and exits that development client. Python
PNG acceptance needs NumPy/Pillow; Node GPU checks need Playwright/Chromium.

2026-10-05 migrated standalone source: build/reobfJar, 180 independent camera
math cases, typed recipe/legacy/invalid-input checks, 12 regenerated resources and
18 angular math checks passed. Real WebGL2 interfaces linked; eight disk
transport/renewal checks, eight particle feedback checks and the seven-preset
Forge-to-workbench-to-Forge typed bridge regression also passed. Fresh Forge development runtime loaded the shaders;
six actual core silhouettes had 0 px maximum independent sphere contour error,
GL errors and depth differences were 0. This covers core placement/initialization,
not all active-lens/player/particle/legacy-item scenes. Older 35-frame world and
632-particle evidence is historical, not certification of this migration.

The Forge dust sprite retains its soft silhouette/display curve; the browser
uses a compact fragment silhouette and shared HDR display pipeline.
The parent skill references/forge-integration.md documents field support;
references/world-regression.md documents full camera/player/360-degree acceptance.
No scientific GR/fluid simulation, entity attraction, synchronization, shaderpack
compatibility or offscreen reconstruction is promised. Particles use straight
projected centers with bent-ray extinction. Local halo is not screen bloom.
