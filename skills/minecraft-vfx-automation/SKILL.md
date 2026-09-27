---
name: minecraft-vfx-automation
description: Create and verify Minecraft Photon visual effects through a compatible MCP-HELPER build, launcher links, the Photon editor, files, and logs. Use for VFX authoring and runtime checks, not ordinary mod implementation.
---

# Minecraft VFX automation

Use this skill to verify the requested Minecraft test environment and discover the safest available programmatic control path. Work only in the named test instance/world and avoid changing production mod source.

## Boundaries

- Follow the user's current GUI instruction. When Computer Use or Sky is prohibited, do not substitute manual mouse or keyboard input. Prefer the user's existing HTTP, MCP, IPC, command, or file interfaces.
- Do not install automation software unless the user asks. Never use online-play tools against a single-player test workflow.
- Do not print launcher database contents, full Java command lines, access tokens, or account identifiers. Launcher process arguments can contain credentials.
- Treat opening an editor, a listening socket, or a newly written file as partial evidence only. Verify an observable response or state change before marking a control path passed.
- Before sending world-changing actions, confirm the active world, control mode, action scope, and rollback or low-impact nature. Prefer read-only status first.

## Workflow

1. Confirm the requested instance, save, and scope. Check existing processes and logs before launching anything; distinguish a process owned by the user from one started for this check.
2. Start the requested instance with its existing launcher/deep-link mechanism. Verify the game process, selected game directory, startup log, and actual world entry separately. A launcher success or title-screen process does not prove that the target save loaded.
3. Discover the Helper interface from its status route, bundled debug page, or installed build documentation. Begin with one short-timeout `GET /api/status`; then inspect the route and command inventory. A TCP listener alone does not prove a working API. If a request hangs, stop probing and inspect logs.
4. Separate read-only methods from methods that simulate input or mutate world state. Query player, world, and screen state first. Only execute actions the user requested or that are demonstrably low impact; verify each with a response, call record, log, or before/after state.
5. Treat loaded world, LAN publication, and MCP control mode as distinct preconditions. Inspect the installed HTTP dispatcher and confirm status again after changing control mode. Use `scripts/invoke-mcp-helper.ps1` for bounded requests; arbitrary game commands are gated behind an explicit switch.
6. Check the installed Photon version, available Helper methods, project format, export path, and test-instance asset directories. Read [references/mcp-helper-integration.md](references/mcp-helper-integration.md) before using version-specific routes. Do not infer that an editor route means an effect can be created or played without GUI. Do not hand-write an undocumented project format. Treat editor preview and command acceptance as partial evidence; verify runtime playback with a permitted visual/programmatic signal plus logs or resulting assets.
7. Use `scripts/photon_workflow.py` only when the installed build exposes the Photon operations it needs. It can prepare RGBA images and ordered atlases, import and bind textures, capture bounded runtime frames, and create hashed revisions with rollback support. It does not generate concept art. Image generation is a separate step when requested.
8. For companion applications, distinguish launch checks (process, window, version/log evidence) from editing or operation checks. The Windows interactive-user wrappers are available for launch or headless commands when a child process cannot access the desktop session. Do not claim an application was operated when interaction was not performed.
9. Report each requested item as passed, failed, partial, or blocked, with evidence and the failure cause. State what is programmatic, what needs GUI, what is read-only, and what interface is missing.

## Helper probe scripts

- `scripts/probe-helper-status.ps1` makes one bounded read-only status request and reports timeouts without retrying.
- `scripts/invoke-mcp-helper.ps1` supports status, calls, player/world/screen queries, screenshots, selected input operations, and explicitly gated commands.
- `scripts/launch-axolotl-quickplay.ps1` is a Windows/Forge-specific optional helper for opening an explicitly named save; inspect its parameters and process behavior before use.

## Compatibility

MCP-HELPER's base HTTP API and Photon authoring operations are separate capabilities. Check the installed build before using any `photon_*` operation. The current public MCP-HELPER release documents the base HTTP/SSE interface; Photon-specific workflows require a compatible Photon-enabled build. See [the MCP-HELPER project](https://github.com/Roxio-sketch/MCP-HELPER) for its current release and API.
