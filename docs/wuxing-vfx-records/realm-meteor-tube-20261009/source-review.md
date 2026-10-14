# Read-only sphere/tube integration review

The inspected native screenshots from 15:55 on 2026-10-09 establish a wider tail and readable player, but still show continuous top/bottom pipe highlights and a bubble-like sphere rim. They are pre-softening runtime evidence. The new GPU report compiles and tests the softened current candidate; the parent task owns the new actual client captures and their visual verdict.

## Material edge

The earlier tube material placed positive Fresnel terms directly in middle/outer density and white tint. Near the silhouette, `abs(N dot V)` approaches zero and these terms reach their maximum exactly at the mesh edge. This explains why its outer extent read as a continuous luminous pipe wall. Reducing those terms and using a grazing alpha fade is a small material change that retains the transverse blue body; it does not require widening the geometry further.

The sphere has a true analytic silhouette and low central transparency. Its former continuous high-radiance rim plus long narrow luminous streams produced the bubble/regular-ring appearance seen in the native captures. The current candidate retains the complete alpha silhouette while lowering rim RGB/emission and breaking only narrow stream cores into short pieces. Broad cyan skirts continue to join those fragments. Integer forward-cap angle harmonics 2 and 3 remove the atan seam risk present with noninteger harmonics.

## Head/tail overlap

The tail's root join fades from 0.20 to 1.05 blocks behind the interpolated body centre. Its nominal middle radius is 65% of orb radius and shares that interpolated world history. This is sufficient geometrical overlap inside the rear sphere. The visible separation in the pre-softening images is chiefly the bright closed sphere edge meeting the bright tube edge, rather than a missing root segment. Do not add root width or move its centre without a new actual image showing a spatial gap.

Current candidate checks confirm a common 112–128 block distance fade for head and tail and quad sorting for the post base mesh. Native/post materials are source-identical. Synthetic equality proves material/matrix equivalence at the tested poses; it cannot certify Minecraft transparent sorting or cross-actor sphere sorting.

## Remaining small adjustment directions

- If the outer pipe edge still appears, reduce its grazing alpha floor or outer layer alpha before changing geometry. Keep the middle body to preserve broad day-sky readability.
- If the sphere continues to read as an even white ring, vary only rim radiance by the existing stable noise field while leaving shell alpha complete and centre alpha unchanged.
- If a root white patch appears, reduce core near-root alpha over the same join interval, keeping the middle cyan layer continuous. Adding more layers would increase overlap.
- At close range, 8 radial segments can reveal silhouette facets despite interpolated smooth normals. If the new image still shows geometric corners, use 10–12 for near observers only; material tuning cannot round the actual mesh contour.

No production source was changed by this review. Independent material/GPU evidence is not a claim of game visual acceptance.
