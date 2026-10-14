param(
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [switch]$AutoValidate,
    [switch]$BuildOnly
)
$ErrorActionPreference = 'Stop'
$oldJava = $env:JAVA_HOME
$oldOptions = $env:JAVA_TOOL_OPTIONS
Push-Location $PSScriptRoot
try {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
    $tempPath = Join-Path $env:USERPROFILE '.codex/tmp/cosmiclab'
    New-Item -ItemType Directory -Path $tempPath -Force | Out-Null
    $tempArgument = '-Djdk.net.unixdomain.tmpdir=' + $tempPath.Replace('\', '/')
    if ($tempArgument.Contains(' ')) { $tempArgument = '"' + $tempArgument + '"' }
    $env:JAVA_TOOL_OPTIONS = ($oldOptions + ' ' + $tempArgument).Trim()
    if ($BuildOnly) {
        & .\gradlew.bat --console plain build
        if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }
        return
    }
    # A shared ForgeGradle client POM may contain Linux classifiers on this host.
    # Extract only official Windows x64 native libraries into this project.
    & .\gradlew.bat --console plain downloadMCMeta
    if ($LASTEXITCODE -ne 0) { throw 'Minecraft metadata download failed' }
    $meta = Get-Content -LiteralPath 'build/downloadMCMeta/version.json' -Raw | ConvertFrom-Json
    $nativeDir = Join-Path $PSScriptRoot 'build/windows-natives'
    New-Item -ItemType Directory -Path $nativeDir -Force | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    foreach ($lib in $meta.libraries | Where-Object { $_.name -match '^org.lwjgl:.*:natives-windows$' }) {
        $artifact = $lib.downloads.artifact
        $jarPath = Join-Path $nativeDir ([IO.Path]::GetFileName($artifact.path))
        if (!(Test-Path -LiteralPath $jarPath) -or (Get-FileHash -LiteralPath $jarPath -Algorithm SHA1).Hash -ne $artifact.sha1) {
            Invoke-WebRequest -Uri $artifact.url -OutFile $jarPath -UseBasicParsing
        }
        if ((Get-FileHash -LiteralPath $jarPath -Algorithm SHA1).Hash -ne $artifact.sha1) { throw 'Native hash mismatch' }
        $zip = [IO.Compression.ZipFile]::OpenRead($jarPath)
        try {
            foreach ($entry in $zip.Entries | Where-Object { $_.Name -like '*.dll' }) {
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $nativeDir $entry.Name), $true)
            }
        } finally { $zip.Dispose() }
    }
    New-Item -ItemType Directory -Path 'run' -Force | Out-Null
    if (!(Test-Path -LiteralPath 'run/options.txt')) {
        [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'run/options.txt'), "onboardAccessibility:false`npauseOnLostFocus:false`n", [Text.UTF8Encoding]::new($false))
    }
    $autoFlag = '-PlabAuto=' + $AutoValidate.IsPresent.ToString().ToLowerInvariant()
    & .\gradlew.bat --console plain runClient $autoFlag '-PlabNativeDir=build/windows-natives'
    if ($LASTEXITCODE -ne 0) { throw 'Minecraft client exited with an error' }
} finally {
    Pop-Location
    $env:JAVA_HOME = $oldJava
    $env:JAVA_TOOL_OPTIONS = $oldOptions
}
