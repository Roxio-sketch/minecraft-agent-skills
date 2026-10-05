# Lightning Lab

Portable Forge 1.20.1 / 47.4.10 / Java 17 client reference, namespace `dev.lightninglab`. It provides `orbit` (grow/spektr/ring), deterministic `strike`, staggered `rain`/`cluster`, and bounded ground discharges. It implements visuals; server damage, weather, block modification and networking need a separate authoritative integration.

Open the sibling `workbench/index.html` in a WebGL2 browser. Copy its exported `recipe.json` to the game instance's `config/lightninglab/recipe.json`. Use `/lightninglab orbit`, `/lightninglab strike [seed]`, `/lightninglab random-strike`, `/lightninglab rain`, `/lightninglab random-rain`, `/lightninglab reload`, or `/lightninglab off`.

```powershell
$env:JAVA_HOME='C:\path\to\jdk-17'
# Create this directory first when your Windows JDK needs a stable Unix socket path.
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\temp -Xms128m -Xmx1024m'
./gradlew.bat --no-daemon --console plain lightningCheck build
```

The recipe has 61 typed fields; old 60-field recipes default `gradientRatio` to 50. The strict parser also accepts legacy `values` string wrappers, rejects unknown/nonfinite/out-of-range/nonintegral values, and preserves custom clusters. Gradient colors multiply independent core/glow tints. Ratio 0 is all end color, 100 all start color, and 50 exactly preserves legacy interpolation.

Grow/ring roots stay fixed, `orbitExtendSpeed` controls blocks/second independently of noise flow, and slow arcs extend their cycle to finish. Source-style spektr retains its moving short-window timing. Cluster defaults remain nine bolts in a 3 × 2.4 block ellipse with zero branches. Ground rays/rings/sparks/flash sample the actual parent tip color, not their local growth progress.

Core ribbons retain shared joins and float-UV subpixel energy compensation. Halo strips are independently convex with round interior joints: per-path RGBA8 `MAX` coverage, then `ADD` emission. Depth attachments match the main target's stencil format. The pass restores GL framebuffer/texture/shader/depth/blend/scissor state and the actual Mojang BlendMode cache object, including null. It uses the default main framebuffer; shader packs/Fabulous/alternate renderers need their own verified adapter.

WebGL2 uses the same color math, physical widths, gain, radial profiles and halo composition. Its simplified camera/screen core joins and JS fractal random sequence differ from Forge, so it is a parameter preview rather than a pixel parity promise. Noise is an Ashima Simplex port; see THIRD_PARTY_NOTICES.md. Ground arcs use a horizontal local plane and sparks have no block collision.

For an explicit standalone GPU check, run `./gradlew.bat runClient -PlightningGpuProbe=true`. It opens only `LightningLab-Mature-GPU-Check`, captures cyan/red/white ground effects, a short thick halo and an ended frame to `run/lightning-lab/gpu-probes/`, restores its options, then exits the development client. Add `-PlightningStencilProbe=true` to test depth-stencil. Both flags are off by default. Keep normal players' clients separate.

Validation commands and tested scope are in the parent skill's `references/validation.md`. The independent template was built and exercised with ordinary and stencil targets on a Forge development client in the 2026-10-05 update; this does not certify Unity pixel parity, multiplayer, shader packs or all target modpacks.
