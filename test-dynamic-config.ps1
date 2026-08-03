#!/usr/bin/env powershell

<#
.SYNOPSIS
    Spring Cloud Config Server + Order Service Test Script

.DESCRIPTION
    Bu script, dinamik konfigürasyon yönetimini (@RefreshScope) test etmek için
    Config Server ve Order Service'i başlatır ve çeşitli endpoint'leri sırası ile çağırır.

.EXAMPLE
    .\test-dynamic-config.ps1
#>

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "Spring Cloud Dynamic Configuration (@RefreshScope) Test Script" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

$CONFIG_SERVER_URL = "http://localhost:8085"
$ORDER_SERVICE_URL = "http://localhost:5001"

# Helper function
function Test-Endpoint {
    param(
        [string]$Method,
        [string]$Url,
        [string]$Description
    )

    Write-Host "`n[$Description]" -ForegroundColor Yellow
    Write-Host "URL: $Method $Url" -ForegroundColor Gray

    try {
        if ($Method -eq "POST") {
            $response = Invoke-RestMethod -Uri $Url -Method Post -ContentType "application/json"
        } else {
            $response = Invoke-RestMethod -Uri $Url -Method Get -ContentType "application/json"
        }

        Write-Host "✅ SUCCESS" -ForegroundColor Green
        Write-Host ($response | ConvertTo-Json | Out-String)
    }
    catch {
        Write-Host "❌ FAILED: $_" -ForegroundColor Red
    }
}

# Step 1: Config Server Health Check
Write-Host "`n[ADIM 1] Config Server Sağlık Kontrolü" -ForegroundColor Cyan
Write-Host "Config Server'ın çalışıp çalışmadığını kontrol et" -ForegroundColor Gray

$configServerReady = $false
for ($i = 1; $i -le 5; $i++) {
    try {
        $response = Invoke-RestMethod -Uri "$CONFIG_SERVER_URL/order-service/dev" -Method Get
        Write-Host "✅ Config Server çalışıyor" -ForegroundColor Green
        $configServerReady = $true
        break
    }
    catch {
        Write-Host "⏳ Bekleniyor... ($i/5)" -ForegroundColor Yellow
        Start-Sleep -Seconds 2
    }
}

if (-not $configServerReady) {
    Write-Host "❌ Config Server başlatılamadı. ÇIKIŞ." -ForegroundColor Red
    exit 1
}

# Step 2: Order Service Health Check
Write-Host "`n[ADIM 2] Order Service Sağlık Kontrolü" -ForegroundColor Cyan
Write-Host "Order Service'in çalışıp çalışmadığını kontrol et" -ForegroundColor Gray

$orderServiceReady = $false
for ($i = 1; $i -le 5; $i++) {
    try {
        $response = Invoke-RestMethod -Uri "$ORDER_SERVICE_URL/actuator/health" -Method Get
        Write-Host "✅ Order Service çalışıyor" -ForegroundColor Green
        $orderServiceReady = $true
        break
    }
    catch {
        Write-Host "⏳ Bekleniyor... ($i/5)" -ForegroundColor Yellow
        Start-Sleep -Seconds 2
    }
}

if (-not $orderServiceReady) {
    Write-Host "❌ Order Service başlatılamadı. ÇIKIŞ." -ForegroundColor Red
    exit 1
}

# Step 3: Get Initial Configuration
Write-Host "`n[ADIM 3] İlk Konfigürasyon Bilgisini Al (Dev Profili)" -ForegroundColor Cyan
Write-Host "Startup sırasında yüklenen dev konfigürasyonunu göster" -ForegroundColor Gray

Test-Endpoint -Method "GET" -Url "$ORDER_SERVICE_URL/api/v1/config/info" -Description "Mevcut Konfigürasyon"

# Step 4: Get Config Status
Write-Host "`n[ADIM 4] Konfigürasyon Durumu" -ForegroundColor Cyan

Test-Endpoint -Method "GET" -Url "$ORDER_SERVICE_URL/api/v1/config/status" -Description "Config Durumu"

# Step 5: Update Config Server (Simulated)
Write-Host "`n[ADIM 5] Config Server'da Konfigürasyon Değiştir" -ForegroundColor Cyan
Write-Host "Not: Gerçek değişiklik için order-service-staging.yml dosyasını güncelleyin" -ForegroundColor Gray
Write-Host "Dosya: src/configserver/src/main/resources/order-service/order-service-staging.yml" -ForegroundColor Yellow

Write-Host "`nStaging profili konfigürasyonu:" -ForegroundColor Green
Write-Host "  - environment: staging" -ForegroundColor Gray
Write-Host "  - database.host: postgres-staging.example.com" -ForegroundColor Gray
Write-Host "  - featureFlags.enableNewOrderProcess: true" -ForegroundColor Gray

Read-Host "Enter tuşuna basarak devam edin (veya manuel olarak config dosyasını güncelleyin)"

# Step 6: Trigger Refresh
Write-Host "`n[ADIM 6] Config Refresh'i Tetikle" -ForegroundColor Cyan
Write-Host "Order Service'e /actuator/refresh POST isteği gönder" -ForegroundColor Gray

Test-Endpoint -Method "POST" -Url "$ORDER_SERVICE_URL/actuator/refresh" -Description "Refresh Tetiklemesi"

# Step 7: Check Updated Configuration
Write-Host "`n[ADIM 7] Güncellenmiş Konfigürasyonu Kontrol Et" -ForegroundColor Cyan
Write-Host "Refresh sonrası yeni konfigürasyonları doğrula" -ForegroundColor Gray

Start-Sleep -Seconds 2
Test-Endpoint -Method "GET" -Url "$ORDER_SERVICE_URL/api/v1/config/info" -Description "Güncellenmiş Konfigürasyon"

# Step 8: Check Actuator Endpoints
Write-Host "`n[ADIM 8] Actuator Endpoint'lerini Keşfet" -ForegroundColor Cyan

$actuatorEndpoints = @(
    "/actuator",
    "/actuator/env",
    "/actuator/configprops",
    "/actuator/health",
    "/actuator/metrics"
)

foreach ($endpoint in $actuatorEndpoints) {
    $url = "$ORDER_SERVICE_URL$endpoint"
    Write-Host "`n✓ $url" -ForegroundColor Gray
}

Write-Host "`n================================================================" -ForegroundColor Cyan
Write-Host "TEST TAMAMLANDI" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Cyan

Write-Host "`n📖 Detaylı Bilgi:" -ForegroundColor Cyan
Write-Host "  • Config Server README: src/configserver/README.md" -ForegroundColor Gray
Write-Host "  • Order Service README: src/orderservice/README.md" -ForegroundColor Gray
Write-Host "  • DynamicConfigProperties: src/orderservice/src/main/java/.../DynamicConfigProperties.java" -ForegroundColor Gray
Write-Host "  • ConfigController: src/orderservice/src/main/java/.../ConfigController.java" -ForegroundColor Gray

Write-Host "`n💡 Sonraki Adımlar:" -ForegroundColor Cyan
Write-Host "  1. order-service-staging.yml dosyasını düzenle" -ForegroundColor Gray
Write-Host "  2. Order Service'e /actuator/refresh isteği gönder" -ForegroundColor Gray
Write-Host "  3. /api/v1/config/info endpoint'ini kontrol et" -ForegroundColor Gray

