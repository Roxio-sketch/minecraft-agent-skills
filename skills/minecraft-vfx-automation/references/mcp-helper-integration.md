# MCP-HELPER integration

MCP-HELPER exposes a local HTTP/SSE API from a running Minecraft client. Start with one bounded, read-only `GET /api/status`, then inspect the installed build's debug page and command inventory before using any other route.

The documented base interface includes status, calls, events, screenshots, and `POST /api/cmd`. Commands that send input or change world state may require MCP control mode. Read-only status or an open TCP port is not proof that a write is safe.

Photon operations such as `photon_create_fx`, `photon_write_fx`, or `photon_play_fx` are optional build-specific extensions; the base release does not guarantee them. Confirm the active build, available operations, Photon version, and world before following a Photon workflow. Do not assume a Photon test build is interchangeable with the public release.

- Project: https://github.com/Roxio-sketch/MCP-HELPER
- Base API documentation: [README](https://github.com/Roxio-sketch/MCP-HELPER/blob/master/README.md)

The scripts in this skill default to `http://127.0.0.1:9876`. Pass another URL when the installed Helper uses a different port.
