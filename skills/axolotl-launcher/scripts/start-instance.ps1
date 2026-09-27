[CmdletBinding()]
param([Parameter(Mandatory)][string]$GameDir, [string[]]$ProfilesRoot=@(),
    [string]$WorldName, [switch]$PlanOnly, [ValidateRange(5,300)][int]$TimeoutSeconds=120)
$ErrorActionPreference='Stop'
$inventory = & (Join-Path $PSScriptRoot 'list-instances.ps1') -ProfilesRoot $ProfilesRoot -AsObject
$path = [IO.Path]::GetFullPath($GameDir).TrimEnd('\')
$matches = @($inventory.instances | Where-Object {$_.gameDir -eq $path})
if ($matches.Count -ne 1) { throw 'GameDir must match exactly one discovered instance; supply ProfilesRoot if needed.' }
$instance = $matches[0]
if (-not $inventory.launcherExists) { throw 'Registered or configured launcher executable not found.' }
if ($WorldName) {
    if ($WorldName -in @('.', '..') -or $WorldName.IndexOfAny([IO.Path]::GetInvalidFileNameChars()) -ge 0) { throw 'WorldName must be one save folder name.' }
    if (-not (Test-Path -LiteralPath (Join-Path $path "saves/$WorldName/level.dat") -PathType Leaf)) { throw 'Requested save does not exist.' }
}
function Find-Client {
    foreach ($process in (Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'")) {
        # Parse only the game directory, never emit the credential-bearing command line.
        if ($process.CommandLine -match '(?:^|\s)--gameDir\s+(?:"([^"]+)"|(\S+))') {
            $value = if ($Matches[1]) {$Matches[1]} else {$Matches[2]}
            if ($value.StartsWith('__THESEUS_UTF8__:')) {
                try {$value=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($value.Substring(17)))} catch {continue}
            }
            if ([IO.Path]::GetFullPath($value).TrimEnd('\') -eq $path) { return [int]$process.ProcessId }
        }
    }
    return $null
}
$clientId=Find-Client
if ($PlanOnly -or $clientId) {
    @{phase= $(if($PlanOnly){'plan'}else{'already_running'}); name=$instance.name; gameDir=$path;
      launchUri=$instance.launchUri; world=$WorldName; clientPid=$clientId; worldVerified=$false} | ConvertTo-Json
    return
}
if ($WorldName) {
    $quickplay=Join-Path $PSScriptRoot '../../minecraft-vfx-automation/scripts/launch-axolotl-quickplay.ps1'
    if (-not (Test-Path -LiteralPath $quickplay)) { throw 'Forge quick-play helper is not installed; ordinary launch remains available without WorldName.' }
    if ($TimeoutSeconds -lt 30) { throw 'Quick-play requires TimeoutSeconds >= 30.' }
    & $quickplay -InstanceUri $instance.launchUri -GameDir $path -WorldName $WorldName -LauncherPath $inventory.launcher -KeepLauncherOpen -TimeoutSeconds $TimeoutSeconds
    return
}
Start-Process -FilePath $inventory.launcher -ArgumentList $instance.launchUri -WindowStyle Hidden | Out-Null
$deadline=[datetime]::UtcNow.AddSeconds($TimeoutSeconds)
do {Start-Sleep -Seconds 1; $clientId=Find-Client} while(-not $clientId -and [datetime]::UtcNow -lt $deadline)
@{phase=$(if($clientId){'client_started'}else{'startup_unconfirmed'}); name=$instance.name;
  gameDir=$path; clientPid=$clientId; worldVerified=$false} | ConvertTo-Json
