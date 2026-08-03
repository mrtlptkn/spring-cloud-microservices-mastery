param(
    [string]$Instance1 = "http://localhost:5001",
    [string]$Instance2 = "http://localhost:5002",
    [string]$EurekaUrl = "http://localhost:8761"
)
function Write-Step([string]$msg) { Write-Host "`n$msg" -ForegroundColor Cyan }
function Write-Ok([string]$msg)   { Write-Host "  OK: $msg" -ForegroundColor Green }
function Write-Warn([string]$msg) { Write-Host "  WARN: $msg" -ForegroundColor Yellow }

Write-Host "============================================" -ForegroundColor Magenta
Write-Host " EUREKA HEALTH CHECK SENARYO TESTI" -ForegroundColor Magenta
Write-Host "============================================" -ForegroundColor Magenta

# TC-01
Write-Step "[TC-01] Baslangic: Her iki instance saglikli mi?"
try {
    $h1 = Invoke-RestMethod "$Instance1/actuator/health"
    $h2 = Invoke-RestMethod "$Instance2/actuator/health"
    Write-Ok "Instance1 (5001): $($h1.status)"
    Write-Ok "Instance2 (5002): $($h2.status)"
} catch { Write-Warn "Instance lara ulasilamadi. Lutfen servisleri baslattiginizdan emin olun." }

# TC-02
Write-Step "[TC-02] Instance 2 DOWN yapiliyor..."
try {
    $res = Invoke-RestMethod -Method Post "$Instance2/api/v1/admin/health/break"
    Write-Ok "Break response: $($res.action)"
    Start-Sleep -Seconds 2
} catch { Write-Warn "DOWN dondurdu - bu beklenen davranis" }

# TC-03
Write-Step "[TC-03] Eureka DOWN'a cekiyor (20sn bekleniyor)..."
Write-Host "  Bekleniyor..." -ForegroundColor Gray
Start-Sleep -Seconds 20
try {
    $apps = Invoke-RestMethod "$EurekaUrl/eureka/apps/order-service" -Headers @{Accept="application/json"}
    foreach ($inst in $apps.application.instance) {
        $color = if ($inst.status -eq "UP") { "Green" } else { "Red" }
        Write-Host "  $($inst.instanceId) -> $($inst.status)" -ForegroundColor $color
    }
} catch { Write-Warn "Eureka parse sorunu. Dashboard: $EurekaUrl" }

# TC-04
Write-Step "[TC-04] 10 istek - sadece saglikli instance yanitlamali..."
$successCount = 0
1..10 | ForEach-Object {
    try {
        Invoke-RestMethod "$Instance1/api/v1" | Out-Null
        $successCount++
        Write-Host "  [$_] OK" -ForegroundColor Green
    } catch { Write-Host "  [$_] HATA" -ForegroundColor Red }
    Start-Sleep -Milliseconds 300
}
Write-Ok "10 istekten $successCount tanesi basarili"

# TC-05
Write-Step "[TC-05] Instance 2 iyilestiriliyor..."
Invoke-RestMethod -Method Post "$Instance2/api/v1/admin/health/fix" | Out-Null
Write-Ok "Fix komutu gonderildi"
Start-Sleep -Seconds 10
try {
    $h2 = Invoke-RestMethod "$Instance2/actuator/health"
    Write-Ok "Instance2 health: $($h2.status)"
} catch { Write-Warn "Instance2 health kontrol edilemedi" }

# TC-06
Write-Step "[TC-06] Toggle endpoint testi..."
$t1 = Invoke-RestMethod -Method Post "$Instance2/api/v1/admin/health/toggle"
Write-Ok "Toggle 1: $($t1.newStatus)"
$t2 = Invoke-RestMethod -Method Post "$Instance2/api/v1/admin/health/toggle"
Write-Ok "Toggle 2: $($t2.newStatus)"

# TC-07
Write-Step "[TC-07] Ozel sebep ile DOWN testi..."
$r7 = Invoke-RestMethod -Method Post "$Instance2/api/v1/admin/health/break?reason=Database+connection+pool+exhausted"
Write-Ok "Reason ile break: $($r7.reason)"
Invoke-RestMethod -Method Post "$Instance2/api/v1/admin/health/fix" | Out-Null
Write-Ok "Instance iyilestirildi"

Write-Host "`n============================================" -ForegroundColor Green
Write-Host " SENARYO TAMAMLANDI" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host " Eureka Dashboard : $EurekaUrl" -ForegroundColor White
Write-Host " Swagger UI       : $Instance1/swagger-ui.html" -ForegroundColor White
Write-Host " Actuator Health  : $Instance1/actuator/health" -ForegroundColor White
