[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ExecutablePath,
    [string]$WorkingDirectory,
    [string[]]$ArgumentList = @(),
    [string]$ArgumentJson,
    [ValidateRange(10,300)][int]$TimeoutSeconds = 120,
    [switch]$Worker,
    [string]$ResultPath
)
$ErrorActionPreference = 'Stop'

if ($Worker) {
    try {
        $arguments = [string[]]([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($ArgumentJson)) | ConvertFrom-Json)
        $info = [Diagnostics.ProcessStartInfo]::new()
        $info.FileName = $ExecutablePath
        $info.WorkingDirectory = $WorkingDirectory
        $info.UseShellExecute = $false
        $info.CreateNoWindow = $true
        $info.RedirectStandardOutput = $true
        $info.RedirectStandardError = $true
        foreach ($argument in $arguments) { $info.ArgumentList.Add([string]$argument) }
        $process = [Diagnostics.Process]::Start($info)
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        [IO.File]::WriteAllText($ResultPath, (@{ok=$true;processId=$process.Id;exitCode=$process.ExitCode;stdout=$stdoutTask.Result;stderr=$stderrTask.Result} | ConvertTo-Json -Depth 3 -Compress))
    } catch {
        [IO.File]::WriteAllText($ResultPath, (@{ok=$false;errorType=$_.Exception.GetType().FullName} | ConvertTo-Json -Compress))
    }
    return
}

if (-not (Test-Path -LiteralPath $ExecutablePath -PathType Leaf)) { throw 'Executable was not found.' }
if (-not $WorkingDirectory) { $WorkingDirectory = Split-Path -Parent ([IO.Path]::GetFullPath($ExecutablePath)) }
if (-not (Test-Path -LiteralPath $WorkingDirectory -PathType Container)) { throw 'Working directory was not found.' }
$pwsh = Join-Path $PSHOME 'pwsh.exe'
$taskName = 'CodexInteractiveCommand-' + [guid]::NewGuid().ToString('N')
$resultPath = Join-Path ([IO.Path]::GetTempPath()) ($taskName + '.json')
$argumentJson = ConvertTo-Json -InputObject @($ArgumentList) -Compress
$argumentBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($argumentJson))
$workerArgs = '-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $PSCommandPath + '" -Worker -ExecutablePath "' + $ExecutablePath + '" -WorkingDirectory "' + $WorkingDirectory + '" -ArgumentJson "' + $argumentBase64 + '" -ResultPath "' + $resultPath + '"'
$action = New-ScheduledTaskAction -Execute $pwsh -Argument $workerArgs
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(2)
$principal = New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
try {
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Force | Out-Null
    Start-ScheduledTask -TaskName $taskName
    $deadline = [datetime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([datetime]::UtcNow -lt $deadline -and -not (Test-Path -LiteralPath $resultPath)) { Start-Sleep -Milliseconds 400 }
    if (-not (Test-Path -LiteralPath $resultPath)) { throw 'The interactive-user command did not finish before timeout.' }
    $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
    if (-not $result.ok) { throw "Command start failed ($($result.errorType))." }
    $result | ConvertTo-Json -Depth 3 -Compress
} finally {
    if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) { Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue; Unregister-ScheduledTask -TaskName $taskName -Confirm:$false }
    Remove-Item -LiteralPath $resultPath -Force -ErrorAction SilentlyContinue
}
