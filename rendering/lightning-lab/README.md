# Lightning Lab

Forge 1.20.1 / Java 17 research harness for three client-side effects:

- `orbit`: short arcs attached to a fixed preview rod. `grow` gives each arc its own fixed local root on the rod and extends/jitters from that arc end; `spektr` reproduces the source-style axial short pulse distributed along the endpoint channel. `ring` maps fixed-root growth onto a circular centerline.
- `strike`: endpoint-preserving midpoint-displacement bolt with recursive forks, deterministic seed, and a fractional-tick envelope.
- `rain`: several staggered near-vertical bolts distributed across a horizontal range, with a hard one-level branch limit.
- All lightning modes support optional `gradientStart` → `gradientEnd` coloring.

Run the HTML preview from `workbench/index.html`. In game, install the built JAR and use `/lightninglab orbit`, `/lightninglab strike [seed]`, `/lightninglab random-strike`, `/lightninglab rain`, `/lightninglab random-rain`, `/lightninglab reload`, or `/lightninglab off`.

```powershell
$env:JAVA_HOME='C:\path\to\jdk-17'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\temp'
./gradlew.bat --no-daemon --console plain build
```

This is a visual client harness. It does not implement server damage, block changes, weather replacement, or shader-pack compatibility guarantees.



The workbench offers a Spektr scene preset, a ring preset, and a pause-on-scrub timeline. Noise is a scalar Ashima Simplex port; see THIRD_PARTY_NOTICES.md. Canvas and Forge share orbit geometry, but use different rasterization and glow. Runtime Minecraft visual parity remains unverified.

## Extension speed and clustered strikes

`orbitExtendSpeed` controls grow/ring tip speed in blocks per second, independently of noise speed. Slow arcs automatically get longer cycles so growth can finish. The source-style spektr window keeps its own original timing.

Use the cluster preset or rainStyle selector for closely grouped seeded landing points. The preset uses nine bolts in a 3 × 2.4 block ellipse with zero branches. Ground effects start on arrival: radial arcs, expanding broken rings, a flash and finite ballistic sparks. Controls expose radius, lifetime, rays, sparks and an enable switch. Forge raycasts block colliders for the hit point; ground arcs approximate a local horizontal plane and sparks have no block collision. Primary and ground lifetimes are both included in cleanup. See evidence/validation.json; Minecraft runtime verification is still pending.
