[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ExecutablePath,
    [string]$WorkingDirectory,
    [string[]]$ArgumentList = @(),
    [string]$ArgumentJson,
    [ValidateRange(20,240)][int]$TimeoutSeconds = 120,
    [switch]$Worker,
    [string]$ResultPath
)
$ErrorActionPreference = 'Stop'

if ($Worker) {
    try {
        $arguments = if ($ArgumentJson) { [string[]]([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($ArgumentJson)) | ConvertFrom-Json) } else { $ArgumentList }
        $info = [Diagnostics.ProcessStartInfo]::new()
        $info.FileName = $ExecutablePath
        $info.WorkingDirectory = $WorkingDirectory
        $info.UseShellExecute = $false
        foreach ($argument in $arguments) { $info.ArgumentList.Add([string]$argument) }
        $process = [Diagnostics.Process]::Start($info)
        [IO.File]::WriteAllText($ResultPath, (@{ok=$true;processId=$process.Id} | ConvertTo-Json -Compress))
    } catch {
        [IO.File]::WriteAllText($ResultPath, (@{ok=$false;errorType=$_.Exception.GetType().FullName} | ConvertTo-Json -Compress))
    }
    return
}

if (-not (Test-Path -LiteralPath $ExecutablePath -PathType Leaf)) { throw 'Application executable was not found.' }
if (-not $WorkingDirectory) { $WorkingDirectory = Split-Path -Parent ([IO.Path]::GetFullPath($ExecutablePath)) }
if (-not (Test-Path -LiteralPath $WorkingDirectory -PathType Container)) { throw 'Application working directory was not found.' }
$pwsh = Join-Path $PSHOME 'pwsh.exe'
if (-not (Test-Path -LiteralPath $pwsh)) { throw 'PowerShell 7 executable was not found.' }
$taskName = 'CodexJavaDesktop-' + [guid]::NewGuid().ToString('N')
$resultPath = Join-Path ([IO.Path]::GetTempPath()) ($taskName + '.json')
$workerPath = $PSCommandPath
$argumentJson = ConvertTo-Json -InputObject @($ArgumentList) -Compress
$argumentBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($argumentJson))
$workerArgs = '-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $workerPath + '" -Worker -ExecutablePath "' + $ExecutablePath + '" -WorkingDirectory "' + $WorkingDirectory + '" -ArgumentJson "' + $argumentBase64 + '" -ResultPath "' + $resultPath + '"'
$action = New-ScheduledTaskAction -Execute $pwsh -Argument $workerArgs
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(2)
$principal = New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
try {
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Force | Out-Null
    Start-ScheduledTask -TaskName $taskName
    $deadline = [datetime]::UtcNow.AddSeconds(20)
    while ([datetime]::UtcNow -lt $deadline -and -not (Test-Path -LiteralPath $resultPath)) { Start-Sleep -Milliseconds 400 }
    if (-not (Test-Path -LiteralPath $resultPath)) { throw 'The interactive-user task did not start the application.' }
    $launch = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
    if (-not $launch.ok) { throw "Application start failed ($($launch.errorType))." }
    $deadline = [datetime]::UtcNow.AddSeconds($TimeoutSeconds)
    $process = $null
    $windowProcess = $null
    $windowReady = $false
    do {
        $process = Get-Process -Id ([int]$launch.processId) -ErrorAction SilentlyContinue
        if (-not $process) { break }
        $allProcesses = @(Get-CimInstance Win32_Process)
        $knownIds = [Collections.Generic.HashSet[uint32]]::new()
        [void]$knownIds.Add([uint32]$launch.processId)
        $changed = $true
        while ($changed) {
            $changed = $false
            foreach ($candidate in $allProcesses) {
                if ($knownIds.Contains([uint32]$candidate.ParentProcessId) -and $knownIds.Add([uint32]$candidate.ProcessId)) { $changed = $true }
            }
        }
        foreach ($processId in $knownIds) {
            $candidateProcess = Get-Process -Id ([int]$processId) -ErrorAction SilentlyContinue
            if ($candidateProcess) {
                $candidateProcess.Refresh()
                $title = $candidateProcess.MainWindowTitle.Trim()
                if ($candidateProcess.MainWindowHandle -ne [IntPtr]::Zero -and $title -and $title -notmatch '(?i)^(splash|error)(\s|$)') { $windowProcess = $candidateProcess; $windowReady = $true; break }
            }
        }
        if ($windowReady) { break }
        Start-Sleep -Seconds 2
    } while ([datetime]::UtcNow -lt $deadline)
    [pscustomobject]@{started=$true;processId=[int]$launch.processId;applicationProcessId=if($windowProcess){$windowProcess.Id}else{$null};windowReady=$windowReady;windowTitle=if($windowProcess){$windowProcess.MainWindowTitle}else{$null};processAlive=[bool]$process} | ConvertTo-Json -Compress
} finally {
    if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) { Unregister-ScheduledTask -TaskName $taskName -Confirm:$false }
    Remove-Item -LiteralPath $resultPath -Force -ErrorAction SilentlyContinue
}
