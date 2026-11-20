# ============================================================
#  LibrarySeat - one-click backend tunnel
#  1) ensure backend on 8080
#  2) ensure cloudflared
#  3) start tunnel
#  4) extract public URL
#  5) rewrite frontend/cloud/app-config.js
#  6) git commit + push (auto deploy on Vercel)
#
#  用法：
#    双击 tunnel.bat                -> 手动启动（结束时会等待回车）
#    powershell -File tunnel.ps1 -NoPause  -> 无人值守（开机自启任务用）
# ============================================================
param([switch]$NoPause)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Write-Step($n, $msg) { Write-Host "[$n] $msg" -ForegroundColor Cyan }

# ---------- 0. sanity ----------
if (-not (Test-Path (Join-Path $root 'frontend\cloud'))) {
  Write-Host "Run this script from the project root." -ForegroundColor Red
  exit 1
}

# ---------- 1. backend on 8080 ----------
Write-Step 1 'checking backend on port 8080 ...'
$listen = netstat -ano | Select-String ':8080\s+.*LISTENING'
if (-not $listen) {
  $jar = Join-Path $root 'backend\target\library-seat.jar'
  if (-not (Test-Path $jar)) {
    Write-Host "   jar not found: $jar" -ForegroundColor Red
    Write-Host "   build it first:  cd backend && mvn clean package -DskipTests" -ForegroundColor Yellow
    exit 1
  }
  Write-Host '   starting backend ...'
  Start-Process -FilePath 'java' -ArgumentList '-Dfile.encoding=UTF-8', '-jar', 'target\library-seat.jar' `
    -WorkingDirectory (Join-Path $root 'backend') -WindowStyle Hidden
  $ok = $false
  for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Seconds 2
    if (netstat -ano | Select-String ':8080\s+.*LISTENING') { $ok = $true; break }
  }
  if (-not $ok) { Write-Host '   backend failed to start (check MySQL)' -ForegroundColor Red; exit 1 }
}
Write-Host '   backend is up' -ForegroundColor Green

# ---------- 2. cloudflared ----------
Write-Step 2 'checking cloudflared ...'
$cfDir = Join-Path $env:LOCALAPPDATA 'LibrarySeat'
$cf    = Join-Path $cfDir 'cloudflared.exe'
if (-not (Test-Path $cf)) {
  Write-Host '   downloading cloudflared (about 53 MB) ...'
  if (-not (Test-Path $cfDir)) { New-Item -ItemType Directory -Path $cfDir -Force | Out-Null }
  & curl.exe -sSL --max-time 600 -o $cf 'https://ghfast.top/https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe'
  if (-not (Test-Path $cf)) { Write-Host '   download failed' -ForegroundColor Red; exit 1 }
}
Write-Host '   cloudflared ready' -ForegroundColor Green

# ---------- 3. stop old tunnel ----------
Write-Step 3 'stopping old tunnel ...'
Get-CimInstance Win32_Process -Filter "name='cloudflared.exe'" -ErrorAction SilentlyContinue |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 2

# ---------- 4. start tunnel ----------
Write-Step 4 'starting tunnel ...'
$log = Join-Path $env:TEMP 'cf_tunnel.log'
Remove-Item $log -ErrorAction SilentlyContinue
Start-Process -FilePath $cf -ArgumentList 'tunnel', '--url', 'http://127.0.0.1:8080', '--no-autoupdate', '--logfile', $log, '--loglevel', 'info' -WindowStyle Hidden

$url = ''
for ($i = 0; $i -lt 40; $i++) {
  Start-Sleep -Seconds 2
  if (Test-Path $log) {
    $m = Select-String -Path $log -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -AllMatches
    if ($m -and $m.Matches.Count -gt 0) { $url = $m.Matches[0].Value; break }
  }
}
if (-not $url) { Write-Host '   no tunnel URL found, see log:' -ForegroundColor Red; Write-Host "   $log"; exit 1 }
Write-Host "   tunnel: $url" -ForegroundColor Green

# ---------- 5. verify ----------
Write-Step 5 'verifying tunnel reaches backend ...'
& ipconfig /flushdns | Out-Null
$code = ''
for ($i = 0; $i -lt 15; $i++) {
  Start-Sleep -Seconds 3
  $code = & cmd.exe /c "curl -sS -o NUL -w %{http_code} --max-time 20 $url/api/system/info 2>NUL"
  if ($code -eq '200') { break }
  Write-Host "   waiting for DNS / edge ... (attempt $($i + 1)/15, code=$code)"
  if ($i % 4 -eq 3) { & ipconfig /flushdns | Out-Null }
}
if ($code -ne '200') { Write-Host "   backend not reachable through tunnel (code=$code)" -ForegroundColor Red; exit 1 }
Write-Host '   api ok' -ForegroundColor Green

# ---------- 6. rewrite cloud config ----------
Write-Step 6 'updating frontend/cloud/app-config.js ...'
$cfgPath = Join-Path $root 'frontend\cloud\app-config.js'
$cfg = @"
/* ============================================================
 * 云端部署专用配置（构建时覆盖到 dist/app-config.js）
 * 自动生成：由 tunnel.bat 写入，请勿手工维护地址
 * 最后更新：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
 * ============================================================ */
window.__APP_CONFIG__ = {
  apiBase: '$url'
}
"@
Set-Content -Path $cfgPath -Value $cfg -Encoding UTF8
Write-Host "   apiBase = $url" -ForegroundColor Green

# ---------- 7. git push ----------
Write-Step 7 'committing and pushing ...'
Push-Location $root
try {
  & git add -A 2>&1 | Out-Null
  & git commit -q -m "chore: update tunnel backend url" 2>&1 | Out-Null
  $env:GIT_TERMINAL_PROMPT = '0'
  & git pushall 2>&1 | Select-String -Pattern 'main -> main|Everything' | ForEach-Object { Write-Host "   $($_.Line.Trim())" }
} finally {
  Pop-Location
}

Write-Host ''
Write-Host 'DONE.' -ForegroundColor Green
Write-Host "Site   : https://superyang.pro"
Write-Host "API    : $url"
Write-Host 'Vercel redeploys in about 1-2 minutes.'
Write-Host 'Keep your PC on and the tunnel running, otherwise the site cannot reach the backend.'
Write-Host ''
if (-not $NoPause) {
  Read-Host 'Press Enter to close'
}