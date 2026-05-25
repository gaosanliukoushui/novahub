param(
    [string]$BaseUrl = "http://localhost:9080",
    [string]$Username = "demo_user",
    [string]$Password = "123456"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()

function Write-Check {
    param([string]$Name, [bool]$Ok, [string]$Detail = "")
    $status = if ($Ok) { "PASS" } else { "FAIL" }
    Write-Host ("{0,-6} {1} {2}" -f $status, $Name, $Detail)
    if (-not $Ok) { throw "$Name failed: $Detail" }
}

function Invoke-Json {
    param(
        [string]$Method = "GET",
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = ""
    )
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $uri = "$BaseUrl$Path"
    if ($Body -ne $null) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 8)
    }
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
}

$health = Invoke-Json -Path "/actuator/health"
Write-Check "health" ($health.status -eq "UP") $health.status

$login = Invoke-Json -Method "POST" -Path "/api/auth/login" -Body @{ username = $Username; password = $Password }
$token = $login.data.token
Write-Check "login" ([bool]$token) $Username

$me = Invoke-Json -Path "/api/users/me" -Token $token
Write-Check "me" ($me.data.username -eq $Username) $me.data.username

$contents = Invoke-Json -Path "/api/contents?page=1&pageSize=5" -Token $token
$records = @($contents.data.records)
Write-Check "contents" ($records.Count -gt 0) ("records=" + $records.Count)
$contentId = $records[0].id

$detail = Invoke-Json -Path "/api/contents/$contentId" -Token $token
Write-Check "detail" ($detail.data.id -eq $contentId) ("id=" + $contentId)

$comments = Invoke-Json -Path "/api/contents/$contentId/comments?limit=5" -Token $token
Write-Check "comments" ($comments.code -eq 200) "ok"

$hotrank = Invoke-Json -Path "/api/hotrank/all?limit=5"
Write-Check "hotrank" (@($hotrank.data).Count -gt 0) ("records=" + @($hotrank.data).Count)

$tags = Invoke-Json -Path "/api/tags/hot?limit=5"
Write-Check "tags" (@($tags.data).Count -gt 0) ("records=" + @($tags.data).Count)

$search = Invoke-Json -Path "/api/search?keyword=Redis&page=1&pageSize=5"
Write-Check "search" ($search.code -eq 200) "ok"

Write-Host "Smoke checks completed against $BaseUrl"
