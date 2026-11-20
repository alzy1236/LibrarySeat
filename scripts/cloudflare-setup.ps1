# ============================================================
#  LibrarySeat - create named Cloudflare Tunnel via API token
#  Steps: create tunnel -> ingress config -> DNS -> Windows service
#  NOTE: messages kept ASCII on purpose (PowerShell 5.1 + UTF-8 source
#        without BOM breaks on non-ASCII literals)
# ============================================================
param(
  [Parameter(Mandatory=$true)][string]$Token,
  [Parameter(Mandatory=$true)][string]$AccountId,
  [Parameter(Mandatory=$true)][string]$ZoneId,
  [string]$TunnelName = 'library-seat',
  [string]$Hostname   = 'api.superyang.pro',
  [string]$LocalUrl   = 'http://localhost:8080'
)

$ErrorActionPreference = 'Continue'
$auth = @('-H', "Authorization: Bearer $Token", '-H', 'Content-Type: application/json')
$base = 'https://api.cloudflare.com/client/v4'
$tmp  = $env:TEMP

function Api($method, $path, $body) {
  $stamp = Get-Random -Minimum 100000 -Maximum 999999
  $out = Join-Path $tmp ("cfapi_$stamp.json")
  if ($body) {
    $bf = Join-Path $tmp ("cfbody_$stamp.json")
    Set-Content -Path $bf -Value $body -Encoding UTF8 -NoNewline
    & curl.exe -sS --max-time 40 @auth -X $method --data-binary "@$bf" -o $out "$base$path" 2>$null
    Remove-Item $bf -ErrorAction SilentlyContinue
  } else {
    & curl.exe -sS --max-time 40 @auth -X $method -o $out "$base$path" 2>$null
  }
  if (Test-Path $out) { $t = Get-Content $out -Raw; Remove-Item $out -ErrorAction SilentlyContinue; return $t }
  return ''
}

"=========================================="
" 1) create/reuse tunnel: $TunnelName"
"=========================================="
$list = Api 'GET' "/accounts/$AccountId/cfd_tunnel" $null
$tunnelId = ''
$pat = '"id":"([0-9a-f]{32})"[^}]*"name":"' + $TunnelName + '"'
if ($list -match $pat) { $tunnelId = $matches[1] }
if ($tunnelId) {
  "   existing tunnel: $tunnelId"
} else {
  $create = Api 'POST' "/accounts/$AccountId/cfd_tunnel" ('{"name":"' + $TunnelName + '","config_src":"cloudflare"}')
  if ($create -match '"id":"([0-9a-f]{32})"') {
    $tunnelId = $matches[1]
    "   created: $tunnelId"
  } else {
    "   CREATE FAILED: $create"
    exit 1
  }
}
$tunnelId | Set-Content (Join-Path $tmp 'tunnel_id.txt')

"`n=========================================="
" 2) ingress: $Hostname -> $LocalUrl"
"=========================================="
$cfg = '{"config":{"ingress":[{"hostname":"' + $Hostname + '","service":"' + $LocalUrl + '"},{"service":"http_status:404"}]}}'
$putCfg = Api 'PUT' "/accounts/$AccountId/cfd_tunnel/$tunnelId/configurations" $cfg
if ($putCfg -match '"success":true') { "   ingress configured" } else { "   CONFIG RESPONSE: $putCfg" }

"`n=========================================="
" 3) DNS: $Hostname"
"=========================================="
$dnsList = Api 'GET' "/zones/$ZoneId/dns_records?name=$Hostname" $null
$dnsBody = '{"type":"CNAME","name":"' + $Hostname + '","content":"' + $tunnelId + '.cfargotunnel.com","proxied":true,"ttl":1}'
if ($dnsList -match '"id":"([0-9a-f]{32})"') {
  $recId = $matches[1]
  $upd = Api 'PUT' "/zones/$ZoneId/dns_records/$recId" $dnsBody
  if ($upd -match '"success":true') { "   updated record $recId" } else { "   UPDATE FAILED: $upd" }
} else {
  $add = Api 'POST' "/zones/$ZoneId/dns_records" $dnsBody
  if ($add -match '"success":true') { "   created CNAME" } else { "   CREATE FAILED: $add" }
}

"`n=========================================="
" 4) tunnel run token"
"=========================================="
$tok = Api 'GET' "/accounts/$AccountId/cfd_tunnel/$tunnelId/token" $null
$runToken = ''
if ($tok -match '"result":"([^"]+)"') { $runToken = $matches[1] }
if (-not $runToken) { "   TOKEN FETCH FAILED: $tok"; exit 1 }
"   got token, length=" + $runToken.Length

"`n=========================================="
" 5) install + start Windows service"
"=========================================="
$cf = Join-Path $env:LOCALAPPDATA 'LibrarySeat\cloudflared.exe'
Get-CimInstance Win32_Process -Filter "name='cloudflared.exe'" -ErrorAction SilentlyContinue |
  Where-Object { $_.CommandLine -like '*--url*' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue; "   stopped quick tunnel pid " + $_.ProcessId }
& $cf service uninstall 2>&1 | Out-Null
$instOut = (& $cf service install $runToken 2>&1 | Out-String)
"   " + $instOut.Trim()
Start-Sleep -Seconds 3
& $cf service start 2>&1 | Out-Null
Start-Sleep -Seconds 6
$svc = Get-Service -Name 'Cloudflared' -ErrorAction SilentlyContinue
if ($svc) { "   service status: " + $svc.Status } else { "   WARN: Cloudflared service not found" }

"`n=========================================="
" 6) verify public endpoint"
"=========================================="
$code = ''
for ($i = 0; $i -lt 20; $i++) {
  Start-Sleep -Seconds 3
  $code = & cmd.exe /c "curl -sS -o NUL -w %{http_code} --max-time 20 https://$Hostname/api/system/info 2>NUL"
  if ($code -eq '200') { break }
  "   waiting for dns/edge ... ($($i+1)/20, code=$code)"
  if ($i % 5 -eq 4) { & ipconfig /flushdns | Out-Null }
}
if ($code -eq '200') {
  "`n   OK: https://$Hostname/api/system/info -> 200"
  "   tunnel id : $tunnelId"
  "   backend   : https://$Hostname"
  exit 0
} else {
  "`n   FAILED code=$code"
  exit 1
}