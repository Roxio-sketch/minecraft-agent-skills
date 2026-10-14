# Native BLOCK wrapper boundary

The three core/rendertype_translucent files are Minecraft 1.20.1's vanilla core shader boilerplate, extracted from the official client resource JAR for this game's own runtime. The wrapper preserves its position, UV, color/lightmap, alpha and fog semantics. Minecraft supplies its original light.glsl and fog.glsl includes; those include files are verified by SHA-256 and are not bundled here.

These small vanilla compatibility templates remain Mojang/Microsoft material under the Minecraft usage terms; they are not presented as original MIT shader algorithms. No third-party shaderpack code is included. The five procedure modules in the workbench, the added surface/environment integration, GPU LUT lifecycle and generation script are the project's original implementation.

hashes.json records the pinned Minecraft 1.20.1 contract. The generator additionally accepts Forge's audited light.glsl patch hash. An override of these core resources requires an explicit adapter and triggers the legacy route.

Run sync-advanced-resources.py after workbench/launch.py --generate-forge. It uses the local templates and Python only; optional --node cross-checks the shared JavaScript pass builder. It does not read the research directory or an author's runtime installation.
