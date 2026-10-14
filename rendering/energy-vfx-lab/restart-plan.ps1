[CmdletBinding()]
param([switch]$PlanOnly = $true)
# Read-only inventory and restart plan. This file never starts or stops a client.
$ErrorActionPreference = 'Stop'
$gamePath = [IO.Path]::GetFullPath('D:/MC/库/red.ghs.axolotl/profiles/ASTRA-TEST').TrimEnd('\')
$worldFolder = 'ASTRA_VFX_TEST'
$launcherSkill = 'C:/Users/MAINTAINER/.codex/skills/axolotl-launcher/scripts'
$inventory = & (Join-Path $launcherSkill 'list-instances.ps1') -AsObject
$selected = @($inventory.instances | Where-Object { $_.gameDir -eq $gamePath })
if ($selected.Count -ne 1 -or $selected[0].name -ne 'ASTRA-TEST') { throw 'Selected instance identity is no longer unique.' }
if (-not (Test-Path -LiteralPath (Join-Path $gamePath "saves/$worldFolder/level.dat") -PathType Leaf)) { throw 'Requested save does not exist.' }
$clientIds = @()
foreach ($candidate in (Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'")) {
    # Extract and emit only gameDir and PID, never authentication-bearing arguments.
    $gameMatch = [regex]::Match($candidate.CommandLine, '(?:^|\s)--gameDir\s+(?:"([^"]+)"|(\S+))')
    if (-not $gameMatch.Success) { continue }
    $dir = if ($gameMatch.Groups[1].Success) { $gameMatch.Groups[1].Value } else { $gameMatch.Groups[2].Value }
    if ($dir.StartsWith('__THESEUS_UTF8__:')) {
        try { $dir = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($dir.Substring(17))) } catch { continue }
    }
    if ([IO.Path]::GetFullPath($dir).TrimEnd('\') -eq $gamePath) { $clientIds += [int]$candidate.ProcessId }
}
$listeners = @()
if ($clientIds.Count) {
    $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.OwningProcess -in $clientIds } |
        Select-Object LocalAddress,LocalPort,OwningProcess)
}
$launcherCommand = "& '$launcherSkill/start-instance.ps1' -GameDir '$gamePath' -WorldName '$worldFolder' -TimeoutSeconds 180"
[ordered]@{
    mode = 'read_only_plan'
    instance = $selected[0].name
    instanceId = $selected[0].instanceId
    gameDir = $gamePath
    world = $worldFolder
    clientPids = $clientIds
    ownedListeners = $listeners
    executable = $inventory.launcher
    launchCommand = $launcherCommand
    launcherPreserved = $true
    launchAllowedOnlyAfter = 'Matching previous client has exited and the new build/recipe have been installed.'
    verify = @(
        'Match the new process gameDir, then its owned listener. GET /api/status must match that PID and type=minecraft-mod.',
        'POST /api/cmd get_connection_info and get_world_info. Confirm requested world_name ASTRA_VFX_TEST, singleplayer server and same player identity.',
        'If control_mode is already true, energyvfx commands may use execute_command. With player control_mode=false, use auto attachment and the mod status log or a narrowly scoped local mod bridge; do not toggle Helper control mode.',
        'GET /api/screenshot; decode only original data:image/png;base64 and save outside the game directory.',
        'Preserve control_mode, mouse_mode, no_pause, GUI and view. No keyboard or mouse commands are needed.'
    )
    limitations = @(
        'Quick-play helper checks localhost:9876 internally; this is insufficient until its /api/status PID is matched to the selected client.',
        'Installed Helper1.0 requires control_mode for execute_command and gui_state; screenshots and world/player/connection reads remain allowed without takeover.',
        'Installed MCP-HELPER 1.0 exposes no dedicated graceful save-and-exit HTTP command.',
        'This plan does not start, stop, install, change camera, reload resources or write game files.'
    )
} | ConvertTo-Json -Depth 6
