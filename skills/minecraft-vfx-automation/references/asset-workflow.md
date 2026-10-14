# Concept assets and Photon test18

Scope: Forge 1.20.1, Photon 1.1.17 / LDLib 1.0.42, Helper test18 and minecraft-mod-mcp 0.4.0-photon-test18. Fabric is excluded from this local user's scope. Recheck these versions and the authorized world before writing. test18 adds nine routes to test17, for 37 Photon MCP tools plus client_resume.

## Image-to-editor workflow

1. When the user supplies a concept and requests creation, inspect the reference and use the available image-generation skill/tool. Generate useful separate transparent particle layers or a consistent ordered frame sequence, not a flattened scene screenshot. Preserve the concept, exact prompt, returned image files, and generation settings supplied by the tool. Do not invent a seed or claim deterministic generation. Verify each returned file exists before continuing. Tool/service failure is not an imported texture.
2. Prepare the actual local PNG outputs with this Skill's `scripts/photon_workflow.py prepare`. Supply `--concept`, `--prompt`, and explicit input order. It preserves RGBA, resizes with premultiplied alpha, pads cells by at least two pixels, and packs row-major. It rejects entirely transparent inputs and warns about opaque backgrounds. It only removes an exact RGB background when `--key RRGGBB` is explicitly chosen; it cannot infer semantic cutouts from arbitrary artwork.
3. `deploy --editor` imports the atlas, creates a native particle graph, binds its texture/UV layout, waits for reload, clears cache, diagnoses, opens or reuses the native editor, and registers the material in the project library. Default deploy is a starting effect; use native graph/editor APIs to author shape, curves, emission, child effects, trails or beams. A single generated image cannot encode those behaviors automatically.
4. Export the actual edited project with `photon_editor_export`, wait for reload, clear cache, and close the editor (it backs up first). Then capture managed playback. A successful export is not visible playback evidence.
5. Snapshot the actual runtime SNBT/binary, full project/library, all referenced managed PNGs, prepared sources/concept/prompt, and evidence. Keep immutable revision folders and ZIPs. Default Minecraft/Photon assets are external dependencies, not redistributed mod contents.

The script requires Pillow and nbtlib; dependencies are in `scripts/requirements-photon.txt`. On the verified local machine the bundled Python is `C:/Users/MAINTAINER/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`; nbtlib is available with `PYTHONPATH=E:/MC-mod制作/MCP-HELPER-Photon-test-20260927/python-libs`. These are local findings, not portable paths. The script works through the local Helper HTTP endpoint and reads evidence/resource files on the same machine.

## Examples

Run from the chosen artifact workspace using the Skill's absolute script path. The shorthand `photon_workflow.py` below refers to that file. Enable control mode after checking the intended world. Use a new output/revision directory each time.

```text
python photon_workflow.py prepare frame0.png frame1.png frame2.png frame3.png --output prepared-v1 --cell 128 --columns 2 --concept concept.png --prompt "Actual generation prompt"
python photon_workflow.py deploy --prepared prepared-v1 --effect demo:flame_v1 --texture demo:flame_v1 --editor
```

`--mode replace` is explicit and requires both existing texture and effect; the API also supports `expectedSha256` for texture import, FX restore, and editor export. Existing assets and projects are backed up before replacement. For one texture use a single prepare input; for an atlas playback is once over particle lifetime, with duration controlled by the emitter's startLifetime. No frame interpolation or semantic animation synthesis is implied.

After native editor authoring:

```json
{"cmd":"photon_editor_export","id":"demo:flame_v1","mode":"replace"}
```

Poll `photon_status` for complete, clear cache, then snapshot while the project is open:

```text
python photon_workflow.py snapshot --effect demo:flame_v1 --game GAME_DIR --output revision-1 --prepared prepared-v1 --editor
```

Close the editor using `photon_editor_close`, then capture at a deliberate visible world position:

```text
python photon_workflow.py capture --effect demo:flame_v1 --output capture-1 --position X Y Z --frames 60 --fps 10 --roi LEFT TOP RIGHT BOTTOM
```

Capture saves all uncached PNGs, actual timestamps/hashes, native runtime telemetry, a contact sheet, and an animated GIF. Frames 2..120, FPS 1..10, framebuffer at most 2073600 pixels. It samples synchronously on the render thread; PNG encoding can reduce actual FPS. Resize with `client_window` before capture when needed. A screen/overlay or world change interrupts capture; cancellation preserves completed frames. Inspect focused pixels/sequence and telemetry, not just whole-frame changes from sky/clouds. Capture is for bounded acceptance, not lossless real-time video or stress benchmarking.

For a final bundle with evidence, reopen the editor and restore `revision-1/project.snbt` through `photon_editor_restore`, snapshot into a fresh revision with `--editor --evidence capture-1`, then:

```text
python photon_workflow.py bundle --revision revision-2 --output flame-v2.zip
python photon_workflow.py restore --revision revision-1 --game GAME_DIR --backup before-rollback --editor
```

Restore requires the effect already exists and an active editor when `--editor` is used. It verifies revision hashes and paths before mutation, creates a separate current-state snapshot, restores imported textures and actual runtime SNBT, reloads/clears cache, restores the project/library, and verifies readback. Runtime SNBT and PNGs are exact; project comparison excludes only unresolved parent UUIDs that Photon replaces with a fresh temporary preview root. Emitter IDs, authored parent links, all parameters and resources remain checked. The archive preserves the original project bytes. Restore is a backed-up sequence of operations, not an atomic multi-file transaction; if an operation fails, keep its error and use the before-rollback revision to recover.

## API details and acceptance boundary

- `photon_import_texture`: logical `id`, raw PNG base64 in `png`, mode create/replace; RGBA required, <=4 MiB, each dimension <=2048. Returns actual `namespace:textures/mcp/path.png`, SHA256 and reload status.
- `photon_bind_texture` / `photon_editor_bind_texture`: logical texture ID, graph main or named child, emitter index, columns/rows 1..32, frames 1..256 <= grid capacity. Grid must divide image dimensions. Animated atlases require particle emitters. Single textures also bind to native trail/beam materials.
- `photon_editor_export`: runtime ID, create/replace, optional expectedSha256. Preserves a full project backup and an old runtime backup; exports actual current FX.
- `photon_editor_restore`: full project `snbt`, <=2 MiB, with old-project backup.
- `photon_restore_fx`: validated runtime root `snbt`, <=2 MiB, create/replace/optional expectedSha256.
- `photon_capture_start/status/stop`: bounded uncached framebuffer session. Status is read-only. Other new routes require control mode.

Engineering acceptance uses deterministic four-color transparent fixtures. It demonstrates ordered red/green/blue/yellow playback, editor-to-runtime export, project/library restore, PNG rollback, revision integrity and continuous-frame evidence. No user concept image was supplied for this release. Do not claim aesthetic fidelity, arbitrary background segmentation, consistent AI animation, or a real concept-to-generated-art acceptance until such a reference is actually used.
