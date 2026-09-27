[CmdletBinding()]
param(
    [ValidateSet('status','calls','player','world','screen','enter-control','exit-control','open-lan','command','click','press-key','screenshot')]
    [string]$Action = 'status',
    [string]$BaseUrl = 'http://127.0.0.1:9876',
    [string]$Command,
    [int]$X,
    [int]$Y,
    [ValidateSet('left','right','middle')][string]$Button = 'left',
    [string]$Key,
    [string]$ScreenshotPath,
    [switch]$AllowWorldMutation,
    [ValidateRange(1,20)][int]$TimeoutSeconds = 4
)
$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
$http = [System.Net.Http.HttpClient]::new()
$http.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
try {
    if ($Action -in @('status','calls','screenshot')) {
        $route = switch ($Action) { 'status' { '/api/status' } 'calls' { '/api/calls' } 'screenshot' { '/api/screenshot' } }
        $response = $http.GetAsync($base + $route).GetAwaiter().GetResult()
    } else {
        $body = switch ($Action) {
            'player' { @{cmd='get_player_info'} }
            'world' { @{cmd='get_world_info'} }
            'screen' { @{cmd='get_screen_buttons'} }
            'enter-control' { @{cmd='enter_control_mode'} }
            'exit-control' { @{cmd='exit_control_mode'} }
            'open-lan' { @{cmd='open_to_lan'} }
            'command' {
                if ([string]::IsNullOrWhiteSpace($Command)) { throw 'Pass -Command with a Minecraft command.' }
                if (-not $AllowWorldMutation -and $Command -notmatch '^\s*/?(time\s+query\s+\w+|weather\s+query|say\s+.+)\s*$') {
                    throw 'This command is outside the read-only/low-impact set. Review its effect, then pass -AllowWorldMutation explicitly.'
                }
                @{cmd='execute_command';command=$Command}
            }
            'click' {
                if ($PSBoundParameters['X'] -eq $null -or $PSBoundParameters['Y'] -eq $null) { throw 'Pass both -X and -Y using coordinates from the current MCP screenshot.' }
                @{cmd='click';x=$X;y=$Y;button=$Button}
            }
            'press-key' {
                if ([string]::IsNullOrWhiteSpace($Key)) { throw 'Pass -Key.' }
                @{cmd='press_key';key=$Key}
            }
            'screenshot' { @{cmd='screenshot'} }
        }
        $json = $body | ConvertTo-Json -Compress
        $content = [System.Net.Http.StringContent]::new($json,[Text.Encoding]::UTF8,'application/json')
        $response = $http.PostAsync($base + '/api/cmd',$content).GetAwaiter().GetResult()
    }
    $payload = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) { throw "Helper returned HTTP $([int]$response.StatusCode)." }
    if ($Action -eq 'screenshot' -and $ScreenshotPath) {
        $data = $payload | ConvertFrom-Json
        if ($data.original -notmatch '^data:image/png;base64,(.+)$') { throw 'Helper did not return a PNG data URI.' }
        [IO.File]::WriteAllBytes([IO.Path]::GetFullPath($ScreenshotPath),[Convert]::FromBase64String($Matches[1]))
        [pscustomobject]@{saved=$true;path=[IO.Path]::GetFullPath($ScreenshotPath)} | ConvertTo-Json -Compress
    } elseif ($Action -eq 'screenshot') { $payload } else { $payload }
} finally { $http.Dispose() }
