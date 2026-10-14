param(
    [string]$JavaHome = "C:\tmp\temurin17\jdk-17.0.20.1+1",
    [switch]$BlackHoleDemo
)
$ErrorActionPreference = "Stop"
$oldJava = $env:JAVA_HOME
$oldOpts = $env:JAVA_TOOL_OPTIONS
Push-Location $PSScriptRoot
try {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
    $sock = "C:/tmp/vfxworkbench-sock"
    New-Item -ItemType Directory -Path $sock -Force | Out-Null
    $env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$sock"
    if ($BlackHoleDemo) { & .\gradlew.bat --console plain runClient --offline -PblackHoleDemo=true }
    else { & .\gradlew.bat --console plain runClient --offline }
    if ($LASTEXITCODE -ne 0) { throw "Minecraft client exited with an error" }
} finally {
    Pop-Location
    $env:JAVA_HOME = $oldJava
    $env:JAVA_TOOL_OPTIONS = $oldOpts
}
