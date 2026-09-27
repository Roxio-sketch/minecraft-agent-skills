[CmdletBinding()]
param([string[]]$ProfilesRoot = @(), [switch]$AsObject)
$ErrorActionPreference = 'Stop'
$hints = Get-Content -LiteralPath (Join-Path $PSScriptRoot '../references/local-paths.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$launcher = $null
$registration = 'Registry::HKEY_CLASSES_ROOT\axolotl\shell\open\command'
if (Test-Path -LiteralPath $registration) {
    $command = (Get-ItemProperty -LiteralPath $registration).'(default)'
    if ($command -match '^"([^"]+\.exe)"') { $launcher = $Matches[1] }
}
if (-not $launcher -or -not (Test-Path -LiteralPath $launcher -PathType Leaf)) { $launcher = $hints.launcher }
$roots = @($ProfilesRoot) + @($hints.profilesRoots)
foreach ($base in @($env:APPDATA, $env:LOCALAPPDATA)) {
    if ($base) { $roots += Join-Path $base 'red.ghs.axolotl/profiles' }
}
$instances = @(); $warnings = @(); $seen = @{}
foreach ($root in ($roots | Select-Object -Unique)) {
    if (-not (Test-Path -LiteralPath $root -PathType Container)) { continue }
    foreach ($directory in (Get-ChildItem -LiteralPath $root -Directory)) {
        $file = Join-Path $directory.FullName 'axolotl_config.json'
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { continue }
        try {
            $config = Get-Content -LiteralPath $file -Raw -Encoding UTF8 | ConvertFrom-Json
            if (-not $config.instance_id) { throw 'Missing instance ID' }
            $game = $directory.FullName
            if ($config.game_dir_override) {
                if (-not [IO.Path]::IsPathFullyQualified([string]$config.game_dir_override)) {
                    throw 'Relative game directory override requires explicit resolution'
                }
                $game = [IO.Path]::GetFullPath([string]$config.game_dir_override)
            }
            $key = [IO.Path]::GetFullPath($game).TrimEnd('\')
            if ($seen.ContainsKey($key)) { continue }; $seen[$key] = $true
            $worlds = @()
            $saveRoot = Join-Path $game 'saves'
            if (Test-Path -LiteralPath $saveRoot -PathType Container) {
                $worlds = @(Get-ChildItem -LiteralPath $saveRoot -Directory | Where-Object {
                    Test-Path -LiteralPath (Join-Path $_.FullName 'level.dat') -PathType Leaf
                } | ForEach-Object { $_.Name })
            }
            $instances += [pscustomobject]@{name=[string]$config.name; instanceId=[string]$config.instance_id;
                gameDir=$key; configFile=$file; worlds=$worlds;
                launchUri=('axolotl://launch/instance/' + [uri]::EscapeDataString([string]$config.instance_id))}
        } catch { $warnings += @{configFile=$file; errorType=$_.Exception.GetType().Name} }
    }
}
$result = [pscustomobject]@{launcher=$launcher; launcherExists=(Test-Path -LiteralPath $launcher -PathType Leaf);
    count=$instances.Count; instances=$instances; warnings=$warnings}
if ($AsObject) { return $result }
$result | ConvertTo-Json -Depth 6
