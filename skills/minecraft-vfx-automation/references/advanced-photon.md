# Photon test17 operations

Target: MCP-HELPER `0.6.0-r18-photon-test17`, Minecraft Forge 1.20.1, Photon 1.1.17 with LDLib 1.0.42. Fabric was explicitly removed from this delivery scope. These are version-specific reflection bridges, not a promise of compatibility with arbitrary Photon releases.

## Authoring and playback

Call `POST /api/cmd` with a JSON `cmd`. Confirm the active world and enable control mode before writes.

- `photon_emitter_template` takes `type` (`particle`, `trail`, `beam`, `empty`) and returns native serialized SNBT. Use that schema instead of inventing fields.
- `photon_write_fx` takes `id`, explicit `mode` (`create` or `replace`) and `document`. `document.emitters` is a list of 1..32 emitter descriptors. Optional `document.subFXs` maps up to 16 names to emitter lists. Descriptors accept `type`, `name`, local `x/y/z`, and a native `config` SNBT patch. Particle emitters also accept the existing scalar shortcuts. Unknown top-level fields and recursive sub-effect references are rejected.
- `photon_read_fx` reads generated SNBT. `photon_update_pair_fx` takes exactly two partial particle descriptors, preserving omitted values.
- Poll `photon_status.reload` after a write, then allow the reload overlay to fade. Legacy command playback also needs `photon_clear_fx_cache` after changing an already cached ID. Managed start deserializes the current active resource directly.
- `photon_start_fx` takes `id`, world `x/y/z`, optional velocity `vx/vy/vz` in blocks per tick, and `maxTicks` (1..12000). It returns a UUID handle. Its scope is `local_client`; it does not broadcast to other players.
- `photon_runtime_status` takes an optional handle. It returns lifetime/state, per-emitter particle counts and render callback counts. These are engine counts, not visible pixels. `photon_move_fx` changes position/velocity. `photon_stop_fx` stops that managed runtime and observed sub emitters. Handles do not cover unrelated effects started through Photon commands.

Example native beam:

```json
{"cmd":"photon_write_fx","id":"demo:beam","document":{"emitters":[{"type":"beam","name":"yellow beam","config":"{color:{_type:\"Color\",number:-256},end:{x:0.0f,y:2.0f,z:0.0f},width:{_type:\"Constant\",number:0.25f}}"}]}}
```

## Diagnostics

`photon_diagnose_fx` checks active resource discovery, schema, native deserialization, material texture/shader preflight and runtime creation without playing. Failure includes `stage` and `error`. Successful preflight explicitly returns `renderVerified:false`. Schema failure may include native readback for comparison.

`photon_errors` with an optional `after` sequence reads the bounded 64-entry error journal. It includes bridge errors and Photon errors logged by the client, with Photon stack frames where available. Renderer log entries have `renderer_log` stage. The journal is global to this client, not attributed to a handle; overwritten entries are indicated by `droppedBefore`. It cannot capture unlogged errors or report after a fatal process exit. Use client logs/crash reports in those cases. Do not describe this as universal renderer exception containment.

The historical `codex_probe.fx` omitted `simulationSpace`. Photon treated its otherwise three-axis `startSize` as legacy scalar data and converted it to zero. A controlled copy adding only `simulationSpace:"World"` rendered correctly. Use native templates that carry the format-discriminating fields.

## Editor

Open through `execute_command` with `command:"photon_editor"` after confirming no editor is open. test17 fixes the former off-thread dispatch. `photon_editor_load` loads a generated resource ID, backing up the current project. `photon_editor_select` opens the configurator for `graph` (default `main`) and `index`.

Query `photon_editor_fields` for currently visible field IDs/values. Pass the current ID and desired `text` to `photon_editor_set_text`. IDs become stale after rebuilding a panel. Invalid numeric text is rejected. Always verify `photon_editor_state` reflects the new model value. `photon_editor_set` supports native SNBT patches for config/transform/name, with identity protection and rollback on deserialization failure.

`photon_editor_save` writes an independent timestamped `.fxproj` under the test game's `ldlib/mcp-backups`, then verifies its NBT readback. `photon_editor_close` and Escape back up before closing. Failure leaves the editor open. This is a Helper integration fix, not a modified Photon or LDLib distribution.

## MCP package and evidence

The delivered `minecraft-mod-mcp` package exposes 28 Photon tools over MCP stdio, plus `client_resume`. Run `node <installed-package>/dist/cli.js mcp`; tools/list and tools/call were exercised against the actual game backend. The local HTTP route remains available. A Mod JAR alone does not register tools in a separately installed older MCP package.

Source acceptance scripts: `scripts/test_photon_live.py`, `test_photon_editor.py`, `test_photon_legacy.py` (nbtlib 2.0.4), and `packages/minecraft-mod-mcp/scripts/test-photon-live.mjs`. Use only an isolated authorized test world; scripts create generated resources and editor backups.

The final test17 suite passed native beam/trail playback, a three-emitter curve/material graph with a named child effect, pair updates, stop, editor numeric readback, backup and Escape. Negative checks covered control gating, unsafe IDs, unknown config fields, recursive child graphs, missing textures and runtime lifetime expiry. Full records and fresh screenshots are in the test17 delivery evidence archive. Resource reload success and screenshot freshness are checked separately.

Two real Forge clients also passed a LAN test in a copied world. The authenticated account joined the temporary offline host; an offline guest could not join an authenticated LAN host and was not counted as a pass. Local managed playback produced yellow pixels only on its calling client (0 versus 3951); native Photon server broadcast produced them on both clients (3390 and 3521); forced removal restored both to zero. `helper_command` sent `photon fx <id> block <x> <y> <z>` and `photon fx remove block <x> <y> <z> true <id>`. Both clients need the matching resource pack. This is two-client local LAN acceptance, not dedicated-server deployment or long-duration load testing. Both game processes exited normally after save/disconnect.

VisualVM 2.2.2 supports native CLI `--start-cpu-sampler <pid>`, then `--snapshot-sampler <pid> --stop-sampler <pid>`. Use the interactive-user launcher on this machine. Confirm a new nonempty `.nps` in `%TEMP%/visualvm_<user>.dat/localhost_<pid>/`; launcher exit codes alone are unreliable for forwarding to an already running VisualVM. The test13 client produced a real CPU snapshot. Prior Effekseer CLI export, Krita image export and IDEA formatting checks remain historical evidence, not full GUI workflow coverage.
