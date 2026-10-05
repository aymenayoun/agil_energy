<#
    capture-fixtures.ps1
    Regenerates every file under frontend/public/assets/mock/ by calling the
    running backend as each of the three demo roles.

    Prerequisites:
      - docker compose up -d   (backend on http://localhost:8082)
      - OTP_ENABLED=false in .env, backend recreated since that change
      - predictions already generated (Previsions IA -> Batch toutes les stations)

    Usage:
      cd C:\Users\DELL\Desktop\energy\energy
      .\scripts\capture-fixtures.ps1
#>

# ============================================================
# CREDENTIALS
# ============================================================
$ADMIN_EMAIL    = 'demo.admin@agil.tn'
$ADMIN_PASSWORD = 'Admin@2026'

$MANAGER_EMAIL    = 'demo.manager@agil.tn'
$MANAGER_PASSWORD = 'Admin@2026'

$STATION_EMAIL    = 'demo.station@agil.tn'
$STATION_PASSWORD = 'Admin@2026'
# ============================================================

$API  = 'http://localhost:8082/api'
$MOCK = (Resolve-Path (Join-Path $PSScriptRoot '..\frontend\public\assets\mock')).Path

$script:written = 0
$script:failed  = @()

function Get-Token {
    param([string]$Email, [string]$Password, [string]$Label)

    $body = @{ email = $Email; password = $Password } | ConvertTo-Json
    try {
        $r = Invoke-RestMethod -Uri "$API/auth/login" -Method Post `
             -ContentType 'application/json' -Body $body -ErrorAction Stop
    } catch {
        Write-Host "  LOGIN FAILED for $Label : $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }

    if ($r.data.requiresOtp) {
        Write-Host "  $Label requires OTP. Set OTP_ENABLED=false then:" -ForegroundColor Red
        Write-Host "    docker compose up -d --force-recreate backend" -ForegroundColor Red
        return $null
    }
    if (-not $r.data.token) {
        Write-Host "  $Label login returned no token." -ForegroundColor Red
        return $null
    }
    Write-Host "  $Label OK (role $($r.data.role))" -ForegroundColor Green
    return $r.data.token
}

function Save-Fixture {
    param(
        [string]$Token,
        [string]$Path,
        [string]$OutFile,
        [string]$Method = 'GET',
        $Body = $null,
        [int]$Limit = 0        # if >0, keep only the first N items of .data
    )

    $dest = Join-Path $MOCK $OutFile
    $dir  = Split-Path $dest -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }

    $headers = @{ Authorization = "Bearer $Token" }

    try {
        if ($Method -eq 'GET') {
            $resp = Invoke-RestMethod -Uri "$API$Path" -Headers $headers -ErrorAction Stop
        } else {
            $json = $Body | ConvertTo-Json -Depth 10
            $resp = Invoke-RestMethod -Uri "$API$Path" -Method $Method -Headers $headers `
                    -ContentType 'application/json' -Body $json -ErrorAction Stop
        }
    } catch {
        Write-Host "    FAIL $Method $Path : $($_.Exception.Message)" -ForegroundColor Yellow
        $script:failed += "$Method $Path ($OutFile)"
        return
    }

    # Trim oversized collections: the demo only needs enough rows to look
    # populated, and these files are downloaded by every visitor.
    if ($Limit -gt 0 -and $resp.data -is [System.Array] -and $resp.data.Count -gt $Limit) {
        $before = $resp.data.Count
        $resp.data = $resp.data[0..($Limit - 1)]
        Write-Host "         (trimmed $before -> $Limit rows)" -ForegroundColor DarkGray
    }

    $resp | ConvertTo-Json -Depth 20 | Set-Content -Path $dest -Encoding UTF8
    $size = [math]::Round((Get-Item $dest).Length / 1KB, 1)
    Write-Host "    ok   $OutFile  ($size KB)"
    $script:written++
}

function Save-403 {
    param([string]$Token)

    $dest = Join-Path $MOCK '403.json'
    try {
        Invoke-RestMethod -Uri "$API/dashboard" -Headers @{ Authorization = "Bearer $Token" } -ErrorAction Stop | Out-Null
        Write-Host "    note: /dashboard did NOT return 403 for the station role" -ForegroundColor Yellow
    } catch {
        # Windows PowerShell exposes the error body on ErrorDetails; the response
        # stream is already consumed by the time we get here.
        $raw = $_.ErrorDetails.Message
        if (-not $raw -and $_.Exception.Response) {
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $raw = $reader.ReadToEnd()
            } catch { }
        }
        if ($raw) {
            $raw | Set-Content -Path $dest -Encoding UTF8
            Write-Host "    ok   403.json"
            $script:written++
            return
        }
        Write-Host "    FAIL could not capture 403 body" -ForegroundColor Yellow
        $script:failed += '403.json'
    }
}

Write-Host ""
Write-Host "Target: $MOCK"
Write-Host ""

# ---------- ADMIN ----------
Write-Host "ADMIN" -ForegroundColor Cyan
$admin = Get-Token $ADMIN_EMAIL $ADMIN_PASSWORD 'ADMIN'
if (-not $admin) { exit 1 }

Save-Fixture $admin '/dashboard'              'admin/dashboard.json'
Save-Fixture $admin '/stations'               'admin/stations.json'
Save-Fixture $admin '/alerts'                 'admin/alerts.json'
Save-Fixture $admin '/alerts/stats/anomalies' 'admin/alerts-stats-anomalies.json'
Save-Fixture $admin '/predictions/regions'    'admin/predictions-regions.json'

Save-Fixture $admin '/stations?region=Tunis'  'stations-region-Tunis.json'
Save-Fixture $admin '/stations/1'             'station-1.json'
Save-Fixture $admin '/audit-logs'             'audit-logs.json'
Save-Fixture $admin '/users'                  'users.json'
Save-Fixture $admin '/fuel-types'             'fuel-types.json'
Save-Fixture $admin '/deliveries'             'deliveries.json'             -Limit 200
Save-Fixture $admin '/deliveries?stationId=1' 'deliveries-station-1.json'   -Limit 200
Save-Fixture $admin '/deliveries?stationId=2' 'deliveries-station-2.json'   -Limit 200
Save-Fixture $admin '/sales?stationId=1'      'sales-station-1.json'        -Limit 300
Save-Fixture $admin '/sales?stationId=2'      'sales-station-2.json'        -Limit 300
Save-Fixture $admin '/stocks/critical'        'stocks-critical.json'
Save-Fixture $admin '/stocks/1'               'stocks-1.json'
Save-Fixture $admin '/stocks/2'               'stocks-2.json'
Save-Fixture $admin '/predictions/ia/health'  'ia-health.json'
Save-Fixture $admin '/predictions/regions/Tunis/fuel-types' 'region-fuel-types.json'
Save-Fixture $admin '/predictions/17?fuelTypeId=4' 'predictions-station-17-4.json'
# fuelTypeId is a required param; these two are interceptor fallbacks only.
Save-Fixture $admin '/predictions/17?fuelTypeId=4' 'predictions-station-17.json'
Save-Fixture $admin '/predictions/1?fuelTypeId=1'  'predictions-station-1.json'

foreach ($n in 1..4) {
    Save-Fixture $admin "/alerts/station/$n" "alerts-station-$n.json"
}

Save-Fixture $admin '/predictions/generate' 'prediction.json' 'POST' `
    @{ stationId = 17; fuelTypeId = 4 }

foreach ($f in 1..5) {
    Save-Fixture $admin '/predictions/generate/region' "predictions-regions-Tunis-$f.json" 'POST' `
        @{ region = 'Tunis'; fuelTypeId = $f }
}

Save-Fixture $admin '/ai/explain' 'ai-explain.json' 'POST' `
    @{ stationId = 17; fuelTypeId = 4; mode = 'default' }

Write-Host "    (AI chat next - each call can take ~60s on CPU)" -ForegroundColor DarkGray
Save-Fixture $admin '/ai/chat' 'chat-default.json' 'POST' `
    @{ question = 'Quelles sont les stations les plus a risque ?'; mode = 'default' }
Save-Fixture $admin '/ai/chat' 'chat-ramadan.json' 'POST' `
    @{ question = 'Quel est l impact du Ramadan sur la consommation ?'; mode = 'default' }
Save-Fixture $admin '/ai/chat' 'chat-rupture.json' 'POST' `
    @{ question = 'Quelles stations risquent une rupture de stock ?'; mode = 'default' }

# ---------- MANAGER ----------
Write-Host ""
Write-Host "MANAGER" -ForegroundColor Cyan
$manager = Get-Token $MANAGER_EMAIL $MANAGER_PASSWORD 'MANAGER'
if ($manager) {
    Save-Fixture $manager '/dashboard'              'manager/dashboard.json'
    Save-Fixture $manager '/stations'               'manager/stations.json'
    Save-Fixture $manager '/alerts'                 'manager/alerts.json'
    Save-Fixture $manager '/alerts/stats/anomalies' 'manager/alerts-stats-anomalies.json'
    Save-Fixture $manager '/predictions/regions'    'manager/predictions-regions.json'
}

# ---------- STATION MANAGER ----------
Write-Host ""
Write-Host "STATION_MANAGER" -ForegroundColor Cyan
$station = Get-Token $STATION_EMAIL $STATION_PASSWORD 'STATION_MANAGER'
if ($station) {
    Save-Fixture $station '/stations'               'station/stations.json'
    Save-Fixture $station '/alerts'                 'station/alerts.json'
    Save-Fixture $station '/alerts/stats/anomalies' 'station/alerts-stats-anomalies.json'
    Save-403     $station
}

# ---------- Summary ----------
Write-Host ""
Write-Host "=============================================="
Write-Host "Fichiers ecrits : $script:written"
if ($script:failed.Count -gt 0) {
    Write-Host "Echecs ($($script:failed.Count)) :" -ForegroundColor Yellow
    $script:failed | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
} else {
    Write-Host "Aucun echec." -ForegroundColor Green
}
Write-Host "=============================================="
