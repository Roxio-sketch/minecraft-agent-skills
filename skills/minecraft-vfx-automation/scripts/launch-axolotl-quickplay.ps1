[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$InstanceUri,
    [Parameter(Mandatory)][string]$GameDir,
    [Parameter(Mandatory)][string]$WorldName,
    [Parameter(Mandatory)][string]$LauncherPath,
    [ValidateRange(30, 300)][int]$TimeoutSeconds = 180,
    [switch]$KeepLauncherOpen,
    [switch]$Worker,
    [string]$ResultPath
)
$ErrorActionPreference = 'Stop'
function Write-Result([hashtable]$Value) { if ($ResultPath) { [IO.File]::WriteAllText($ResultPath, ($Value | ConvertTo-Json -Depth 4)) } }
if (-not $Worker) {
    $taskName = 'CodexMinecraftQuickPlay-' + [guid]::NewGuid().ToString('N')
    $result = Join-Path ([IO.Path]::GetTempPath()) ($taskName + '.json')
    $pwsh = Join-Path $PSHOME 'pwsh.exe'
    if (-not (Test-Path -LiteralPath $pwsh)) { throw 'PowerShell 7 executable not found for the temporary task.' }
    $keepLauncherFlag = if ($KeepLauncherOpen) { ' -KeepLauncherOpen' } else { '' }
    $workerArgs = '-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $PSCommandPath + '" -Worker' + $keepLauncherFlag + ' -InstanceUri "' + $InstanceUri + '" -GameDir "' + $GameDir + '" -WorldName "' + $WorldName + '" -LauncherPath "' + $LauncherPath + '" -ResultPath "' + $result + '" -TimeoutSeconds ' + $TimeoutSeconds
    $action = New-ScheduledTaskAction -Execute $pwsh -Argument $workerArgs
    $trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(3)
    $user = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited
    try {
        Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Force | Out-Null
        Start-ScheduledTask -TaskName $taskName
        $deadline = [datetime]::UtcNow.AddSeconds($TimeoutSeconds)
        while ([datetime]::UtcNow -lt $deadline -and -not (Test-Path -LiteralPath $result)) { Start-Sleep -Milliseconds 500 }
        if (-not (Test-Path -LiteralPath $result)) { throw 'Temporary task did not start the client before timeout.' }
        $status = Get-Content -LiteralPath $result -Raw | ConvertFrom-Json
        $status | Add-Member -NotePropertyName schedulerTask -NotePropertyValue $taskName
        $output = $status | ConvertTo-Json -Depth 5
        if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) { Unregister-ScheduledTask -TaskName $taskName -Confirm:$false }
        Remove-Item -LiteralPath $result -Force -ErrorAction SilentlyContinue
        $output
    }
    catch { if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) { Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue; Unregister-ScheduledTask -TaskName $taskName -Confirm:$false }; Remove-Item -LiteralPath $result -Force -ErrorAction SilentlyContinue; throw }
    return
}
$phase = 'preflight'
try {
    if (-not (Test-Path -LiteralPath $LauncherPath)) { throw 'Axolotl launcher executable not found.' }
    if (-not (Test-Path -LiteralPath $GameDir)) { throw 'Minecraft game directory not found.' }
    $expectedGameDir = [IO.Path]::GetFullPath($GameDir).TrimEnd('\')
    if (-not $KeepLauncherOpen) {
        Get-CimInstance Win32_Process | Where-Object { $_.Name -eq [IO.Path]::GetFileName($LauncherPath) -and $_.ExecutablePath -eq $LauncherPath } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
        Start-Sleep -Seconds 2
    }
    $preexistingPids = [Collections.Generic.HashSet[uint32]]::new()
    foreach ($existing in (Get-CimInstance Win32_Process -Filter "Name='javaw.exe'")) { [void]$preexistingPids.Add([uint32]$existing.ProcessId) }
    $phase = 'launcher'
    Start-Process -FilePath $InstanceUri | Out-Null
    $argvType = 'using System;using System.Runtime.InteropServices;public static class QuickPlayArgv{[DllImport("shell32.dll",SetLastError=true)]public static extern IntPtr CommandLineToArgvW([MarshalAs(UnmanagedType.LPWStr)]string commandLine,out int argc);[DllImport("kernel32.dll")]public static extern IntPtr LocalFree(IntPtr pointer);}'
    Add-Type -TypeDefinition $argvType
    function Get-Arguments([string]$CommandLine) {
        $count = 0; $pointer = [QuickPlayArgv]::CommandLineToArgvW($CommandLine, [ref]$count)
        if ($pointer -eq [IntPtr]::Zero) { throw 'Unable to parse the launcher client arguments.' }
        try { $items = New-Object string[] $count; for ($index=0; $index -lt $count; $index++) { $slot=[Runtime.InteropServices.Marshal]::ReadIntPtr($pointer,($index * [IntPtr]::Size));$items[$index]=[Runtime.InteropServices.Marshal]::PtrToStringUni($slot) }; return ,$items }
        finally { [void][QuickPlayArgv]::LocalFree($pointer) }
    }
    function Decode-LauncherArgument([string]$Value) { $prefix='__THESEUS_UTF8__:';if($Value.StartsWith($prefix)){return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Value.Substring($prefix.Length)))};return $Value }
    $deadline=[datetime]::UtcNow.AddSeconds($TimeoutSeconds);$source=$null;$sourceArgs=$null
    while([datetime]::UtcNow -lt $deadline -and -not $source){
        foreach($candidate in (Get-CimInstance Win32_Process -Filter "Name='javaw.exe'")){
            if($preexistingPids.Contains([uint32]$candidate.ProcessId)){continue}
            if([string]::IsNullOrWhiteSpace($candidate.CommandLine)){continue}
            if(-not $candidate.CommandLine.Contains('--gameDir')){continue};$candidateArgs=Get-Arguments $candidate.CommandLine
            for($index=1;$index -lt $candidateArgs.Length-1;$index++){
                if($candidateArgs[$index] -eq '--gameDir' -and (Decode-LauncherArgument $candidateArgs[$index+1]) -eq $expectedGameDir){$source=$candidate;$sourceArgs=$candidateArgs;break}
            };if($source){break}
        };if(-not $source){Start-Sleep -Seconds 1}
    }
    if(-not $source){throw 'Axolotl did not start a Java client for the requested game directory.'}
    $modMain=[Array]::IndexOf($sourceArgs,'com.modrinth.theseus.MinecraftLaunch');$forgeMain=[Array]::IndexOf($sourceArgs,'cpw.mods.bootstraplauncher.BootstrapLauncher',($modMain+1))
    if($modMain -lt 1 -or $forgeMain -le $modMain){throw 'Could not identify Axolotl and Forge launch entrypoints.'}
    $launchArgs=[Collections.Generic.List[string]]::new()
    for($index=1;$index -lt $modMain;$index++){$value=$sourceArgs[$index];if($value.StartsWith('-javaagent:') -or $value.StartsWith('-Djava.system.class.loader=') -or $value.StartsWith('-Dmodrinth.internal.ipc.') -or $value.StartsWith('-Dmodrinth.internal.quickPlay.')){continue};$launchArgs.Add((Decode-LauncherArgument $value))}
    $launchArgs.Add('cpw.mods.bootstraplauncher.BootstrapLauncher');$skipValue=$false
    for($index=$forgeMain+1;$index -lt $sourceArgs.Length;$index++){if($skipValue){$skipValue=$false;continue};$value=$sourceArgs[$index];if($value -eq '--quickPlayPath'){$skipValue=$true;continue};if($value -eq '--quickPlaySingleplayer'){throw 'Launcher already provided a quick-play world argument.'};$launchArgs.Add((Decode-LauncherArgument $value))}
    $launchArgs.Add('--quickPlaySingleplayer');$launchArgs.Add($WorldName)
    $phase='quick-play';$log=Join-Path $expectedGameDir 'logs\latest.log';$logOffset=if(Test-Path -LiteralPath $log){(Get-Item -LiteralPath $log).Length}else{0};Stop-Process -Id $source.ProcessId -Force;Start-Sleep -Milliseconds 500
    $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=$source.ExecutablePath;$start.WorkingDirectory=$expectedGameDir;$start.UseShellExecute=$false;foreach($argument in $launchArgs){$start.ArgumentList.Add($argument)};$game=[Diagnostics.Process]::Start($start);$gamePid=$game.Id
    $phase='world-load';$worldEntered=$false;$crashed=$false;$loadDeadline=[datetime]::UtcNow.AddSeconds($TimeoutSeconds);$apiTimer=[datetime]::UtcNow.AddSeconds(-10);$http=[System.Net.Http.HttpClient]::new();$http.Timeout=[TimeSpan]::FromMilliseconds(1500);$worldBody='{"cmd":"get_world_info"}'
    while([datetime]::UtcNow -lt $loadDeadline){
        if(-not(Get-Process -Id $gamePid -ErrorAction SilentlyContinue)){$crashed=$true;break}
        if(Test-Path -LiteralPath $log){$stream=[IO.File]::Open($log,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite);try{if($stream.Length -lt $logOffset){$logOffset=0};[void]$stream.Seek($logOffset,[IO.SeekOrigin]::Begin);$reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8,$true,1024,$true);$freshText=$reader.ReadToEnd();$reader.Dispose();$logOffset=$stream.Position;if($freshText -match '#@!@# Game crashed!|Preparing crash report'){$crashed=$true;break}}finally{$stream.Dispose()}}
        if([datetime]::UtcNow -ge $apiTimer){$apiTimer=[datetime]::UtcNow.AddSeconds(5);try{$content=[System.Net.Http.StringContent]::new($worldBody,[Text.Encoding]::UTF8,'application/json');$response=$http.PostAsync('http://127.0.0.1:9876/api/cmd',$content).GetAwaiter().GetResult();if($response.IsSuccessStatusCode){$body=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult();if($body.Contains($WorldName)){$worldEntered=$true;break}}}catch{}}
        Start-Sleep -Seconds 2
    }
    $http.Dispose()
    Write-Result @{ok=$worldEntered;phase=$phase;world=$WorldName;gamePid=$gamePid;integratedServerStarted=$worldEntered;crashed=$crashed;detail=if($worldEntered){'The live MCP get_world_info response returned the requested world name.'}elseif($crashed){'The client crashed before world entry; inspect the crash report without printing launcher arguments.'}else{'World entry was not confirmed before timeout.'}}
}
catch { $message=$_.Exception.Message;$message=[regex]::Replace($message,'(?i)(access[_ -]?token|bearer)\\s*[:=]?\\s*\\S+','$1=[redacted]');Write-Result @{ok=$false;phase=$phase;world=$WorldName;errorType=$_.Exception.GetType().FullName;detail=$message} }
