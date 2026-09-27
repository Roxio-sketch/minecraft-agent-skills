[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:9876',
    [ValidateRange(1, 30)]
    [int]$TimeoutSeconds = 4
)

$ErrorActionPreference = 'Stop'
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
$timer = [System.Diagnostics.Stopwatch]::StartNew()
$exitCode = 0
$uri = $BaseUrl.TrimEnd('/') + '/api/status'

try {
    $uri = [Uri]::new($uri)
    $response = $client.GetAsync($uri).GetAwaiter().GetResult()
    $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $summary = $null

    try {
        $status = $body | ConvertFrom-Json -ErrorAction Stop
        $summary = $status | Select-Object version, loader, forgeVersion, port, control_mode, mcp_available, helper_available, structure_available
    }
    catch {
        $summary = 'Response was not valid JSON.'
    }

    [ordered]@{
        ok             = $response.IsSuccessStatusCode
        url            = $uri.AbsoluteUri
        status_code    = [int]$response.StatusCode
        elapsed_ms     = $timer.ElapsedMilliseconds
        status_summary = $summary
    } | ConvertTo-Json -Depth 4

    if (-not $response.IsSuccessStatusCode) {
        $exitCode = 1
    }
}
catch {
    $rootCause = $_.Exception
    while ($rootCause.InnerException) {
        $rootCause = $rootCause.InnerException
    }

    [ordered]@{
        ok          = $false
        url         = [string]$uri
        elapsed_ms  = $timer.ElapsedMilliseconds
        error_type  = $rootCause.GetType().FullName
        error       = $rootCause.Message
    } | ConvertTo-Json -Depth 3
    $exitCode = 1
}
finally {
    $client.Dispose()
}

exit $exitCode
