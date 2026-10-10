# Black Hole Material Lab

Standalone client-side Forge 1.20.1 / Java 17 example under dev.blackholelab.
The world compositor includes five disk styles, core/disk/layered-halo gradients,
white bipolar transport jets, expanding spherical shells, a stationary core corona, stable dust identities, a 360-degree angular environment
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

/blackhole world raycasts the configured segment and fixes the world center.
/blackhole white selects the enhanced horizontal white-ring preset and places it.
/blackhole black restores the standalone black preset and places it.
/blackhole off stops and releases resources. Existing size/color/lens/rotation/
infall/particles/particle_speed/halo/tilt/showcase/items commands remain available.
/blackhole lens_players true explicitly permits player lensing; false protects
complete player models. There are no jet_speed, shell_speed or pause commands.

/blackhole import reads config/blackholelab/recipe.json. Import accepts validated
preview/v1, preview/v2 and world/v1 historical black recipes plus world/v2.
/blackhole export now writes typed black-hole-world/v2. The enhanced white preset
has luminous core emission and its own soft finite corona, outward disk/particle
flow, bilateral axial transport and expanding light shells. A partial v2
form:white-ring selects the current preset; complete old 66/72-field v2 files
preserve supplied values and keep omitted new effects disabled. Upgraded white
rendering does not promise the old white recipe's pixels. Historical schemas
reject white and new-field extensions. The browser supports only the historical
world/v1 black bridge, not the current v2 export or the white-hole path.

Use the single-user development file black-hole-lab/command.json for new field
settings. Requests use a top-level action and settings fields; after each write
wait for a new command-result.json. To hold current jet/shell phases, while in
normal live white mode, write {"action":"settings","time":-1,"jetSpeed":0,"shellSpeed":0}.
Restore with {"action":"settings","time":-1,"jetSpeed":1.15,"shellSpeed":0.7}.
coreGlow/coreGlowRadius are stationary world material settings, independent of
the halo and shell switches. A settings.time >=0 request is a fixed screenshot
probe and bypasses the flow clocks; time:-1 exits that probe. Re-importing recipes
resets clocks, so repeated import is not a continuous pause/resume mechanism.
New material-only settings preserve command-mutated tilt/rotation/infall and
particleSpeed. This bridge is not a concurrent recipe API.

Browser-only background/camera/HDR bloom and midpoint tint remain metadata.
The item verify audit is still restricted to its authorized ASTRA_VFX_TEST save;
do not remove that guard for an unselected user save. This enhanced standalone
template has not been launched in an independent Minecraft runtime; production
workbench validation does not certify all template commands or item paths.

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

## Radiant white core

The enhanced white default uses coreGlow=1.2 and coreGlowRadius=2.6. Core-only captured radiance feeds half-resolution RGBA16F targets, two separable diffusion scales and linear optical composition with faint short aperture rays. The source inherits the visible artistic core terrain policy; protected entities are excluded from emission, while optical spill may cover foreground pixels. Radius controls a finite corona and projected blur width, not a strict world-space cutoff for optical tails. This is not dynamic terrain lighting. New production verification is recorded under white-hole-radiance-20261007; the independent template has not been launched in Minecraft.

Current 75-field white default follows the supplied recipe: core 0.28, emission 3.95, glow 0.8/1.05, classic disk, no jets or independent particles, time 37862.8. shellEnabled toggles expanding shells without destroying parameters. Narrow dust disks use width-relative edges and weighted volume noise. Production dropdown UI is not part of this standalone command/JSON template. See white-hole.md for compatibility and verification scope.
