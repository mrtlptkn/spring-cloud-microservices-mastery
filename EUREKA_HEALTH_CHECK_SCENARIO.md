# Eureka Health Check & Unhealthy Instance Simulation Senaryosu

> **Problem:** Eureka dashboard'da her iki instance `UP` görünmesine rağmen kullanıcılar zaman zaman hata alıyor.  
> **Sebebi:** Yeni deploy edilen instance sağlıksız çalışıyor ama Eureka'ya yanlış durum bildiriyor.  
> **Çözüm:** Actuator Health → Eureka Health Check entegrasyonu + LoadBalancer sağlık filtresi.

---

## Mimari Akış

```
[order-service:5001] ──heartbeat──▶ [Eureka:8761]
[order-service:5002] ──heartbeat──▶ [Eureka:8761]
                                          │
                                  (registry cache)
                                          │
[Gateway / Feign Client] ──fetch──▶ [LoadBalancer]
                                          │
                               health filter devrede
                                    │           │
                              [5001: UP]    [5002: DOWN → skip]
                                    │
                             ✅ sadece sağlıklı instance'a trafik
```

---

## Eklenen / Değiştirilen Dosyalar

| Dosya | Değişiklik |
|---|---|
| `eurekaserver/src/main/resources/application.yml` | Self-preservation kapalı, hızlı eviction |
| `orderservice/src/main/resources/application.yml` | Eureka healthcheck, lease süresi, LoadBalancer, Feign retry |
| `orderservice/.../health/SimulatedHealthIndicator.java` | Programatik DOWN/UP toggle |
| `orderservice/.../controller/HealthToggleController.java` | REST endpoint ile health toggle |

---

## Servis Başlatma Sırası

```powershell
# Terminal 1 - Eureka Server
cd src\eurekaserver
.\mvnw.cmd spring-boot:run

# Terminal 2 - Order Service Instance 1 (port 5001)
cd src\orderservice
.\mvnw.cmd spring-boot:run

# Terminal 3 - Order Service Instance 2 (port 5002)
cd src\orderservice
$env:SERVER_PORT="5002"
.\mvnw.cmd spring-boot:run
```

---

## Test Case'leri

### TC-01: Başlangıç Durumu — Her İki Instance UP

**Amaç:** Sistem normal durumda her iki instance da sağlıklı olduğunda Eureka'nın her ikisini de UP gösterdiğini doğrulamak.

**Adımlar:**
```powershell
# Her iki instance health kontrolü
Invoke-RestMethod "http://localhost:5001/actuator/health"
Invoke-RestMethod "http://localhost:5002/actuator/health"

# Eureka'da her iki instance'ı listele
Invoke-RestMethod "http://localhost:8761/eureka/apps/order-service"
```

**Beklenen Sonuç:**
- Her iki `/actuator/health` → `{"status":"UP"}`
- Eureka'da `order-service` altında iki instance, her ikisi `UP` statüsünde
- Eureka Dashboard: `http://localhost:8761` → `order-service` → 2 instance

---

### TC-02: Instance 2'yi Programatik Olarak DOWN Yap

**Amaç:** `SimulatedHealthIndicator` üzerinden instance'ı sağlıksız hale getirmek ve bunun actuator'a yansıdığını doğrulamak.

**Adımlar:**
```powershell
# Instance 2'yi DOWN yap
Invoke-RestMethod -Method Post "http://localhost:5002/api/v1/admin/health/break"

# Actuator health kontrol
Invoke-RestMethod "http://localhost:5002/actuator/health"

# Simülasyon durumunu kontrol et
Invoke-RestMethod "http://localhost:5002/api/v1/admin/health/status"
```

**Beklenen Sonuç:**
```json
// /actuator/health
{
  "status": "DOWN",
  "components": {
    "simulatedHealth": {
      "status": "DOWN",
      "details": { "reason": "Simulated failure", ... }
    }
  }
}
```

---

### TC-03: Eureka'nın Instance 2'yi DOWN'a Çekmesi

**Amaç:** Eureka Client Health Check entegrasyonunun actuator durumunu Eureka'ya ilettiğini doğrulamak.

**Beklenti:** TC-02'den sonra ~10-15 saniye beklendiğinde Eureka instance 2'yi `DOWN` veya `OUT_OF_SERVICE` olarak işaretler.

**Adımlar:**
```powershell
# Instance 2'yi DOWN yap
Invoke-RestMethod -Method Post "http://localhost:5002/api/v1/admin/health/break"

# 15 saniye bekle (lease-expiration-duration-in-seconds: 15)
Start-Sleep -Seconds 20

# Eureka'nın instance listesini kontrol et
Invoke-RestMethod "http://localhost:8761/eureka/apps/order-service" | Select-Xml "//instance" | ForEach-Object { $_.Node.instanceId + " -> " + $_.Node.status }
```

**Beklenen Sonuç:**
```
order-service:5001 -> UP
order-service:5002 -> DOWN
```

Eureka Dashboard: `http://localhost:8761` → Instance 2 kırmızı veya listeden çıkmış

---

### TC-04: LoadBalancer Sağlıklı Instance'a Yönlendiriyor

**Amaç:** Instance 2 DOWN iken tüm trafiğin instance 1'e gittiğini doğrulamak.

**Adımlar:**
```powershell
# Instance 2'yi DOWN yap ve bekle
Invoke-RestMethod -Method Post "http://localhost:5002/api/v1/admin/health/break"
Start-Sleep -Seconds 20

# 10 istek at, hepsinin instance 1'den gelmesi lazım
1..10 | ForEach-Object {
    $res = Invoke-RestMethod "http://localhost:5001/api/v1"
    Write-Host "Response: $res"
    Start-Sleep -Milliseconds 500
}
```

**Beklenen Sonuç:**
- Tüm yanıtlar başarılı (hata yok)
- Gateway üzerinden gidiyorsa tüm istekler instance 1 (5001) tarafından karşılanır

---

### TC-05: Instance 2'nin İyileştirilmesi (Recovery)

**Amaç:** DOWN olan instance'ın geri UP'a dönmesi ve Eureka'nın yeniden kaydetmesini doğrulamak.

**Adımlar:**
```powershell
# Instance 2'yi iyileştir
Invoke-RestMethod -Method Post "http://localhost:5002/api/v1/admin/health/fix"

# Actuator kontrol
Invoke-RestMethod "http://localhost:5002/actuator/health"

# ~5 saniye bekle (lease-renewal-interval-in-seconds: 5)
Start-Sleep -Seconds 10

# Eureka'da tekrar UP görünmeli
Invoke-RestMethod "http://localhost:8761/eureka/apps/order-service" | Select-Xml "//instance" | ForEach-Object { $_.Node.instanceId + " -> " + $_.Node.status }
```

**Beklenen Sonuç:**
```
order-service:5001 -> UP
order-service:5002 -> UP
```

---

### TC-06: Toggle Endpoint Testi

**Amaç:** `/toggle` endpoint'inin durumu tersine çevirdiğini doğrulamak.

**Adımlar:**
```powershell
# Başlangıç: UP
Invoke-RestMethod "http://localhost:5002/api/v1/admin/health/status"
# {"simulatedHealthy": true}

# Toggle → DOWN
Invoke-RestMethod -Method Post "http://localhost:5002/api/v1/admin/health/toggle"
# {"action": "HEALTH_TOGGLED", "newStatus": "DOWN"}

Invoke-RestMethod "http://localhost:5002/actuator/health"
# {"status": "DOWN"}

# Toggle → UP
Invoke-RestMethod -Method Post "http://localhost:5002/api/v1/admin/health/toggle"
# {"action": "HEALTH_TOGGLED", "newStatus": "UP"}
```

---

### TC-07: Özel Sebep ile DOWN (reason parametresi)

**Amaç:** Farklı hata senaryolarını simüle etmek (DB bağlantısı, harici servis, disk doldu vb.).

**Adımlar:**
```powershell
# Veritabanı hatası simülasyonu
Invoke-RestMethod -Method Post "http://localhost:5002/api/v1/admin/health/break?reason=Database+connection+pool+exhausted"

Invoke-RestMethod "http://localhost:5002/actuator/health"
# details.reason: "Database connection pool exhausted"

# Memory hatası simülasyonu
Invoke-RestMethod -Method Post "http://localhost:5002/api/v1/admin/health/break?reason=Out+of+memory+error"

# Downstream servis hatası
Invoke-RestMethod -Method Post "http://localhost:5002/api/v1/admin/health/break?reason=Product+service+unreachable"
```

---

### TC-08: Eureka Self-Preservation Etkisi

**Amaç:** `enable-self-preservation: false` ile sağlıksız instance'ların hızlı temizlendiğini görmek.

**Açıklama:**  
Self-preservation `true` olsaydı, Eureka "fazla heartbeat kaybı var, ağ sorunu olabilir" diyerek instance'ları registry'den silmezdi. Bu TC, neden simülasyon ortamında kapalı tutulduğunu gösterir.

**Adımlar:**
```powershell
# Instance 2 DOWN yap
Invoke-RestMethod -Method Post "http://localhost:5002/api/v1/admin/health/break"

# eviction-interval-timer-in-ms: 5000 → 5 saniye içinde eviction başlamalı
# lease-expiration-duration-in-seconds: 15 → 15 saniye heartbeat gelmezse düşür
Start-Sleep -Seconds 20

# Eureka'dan silinmiş olmalı
Invoke-RestMethod "http://localhost:8761/eureka/apps/order-service"
```

**Beklenen:** Instance 2 Eureka listesinden tamamen çıkmış veya DOWN statüsünde.

---

## Tam Otomatik Test Scripti

Tüm test case'lerini sırasıyla çalıştıran script:

```powershell
Write-Host "=== EUREKA HEALTH CHECK SENARYO TESTI ===" -ForegroundColor Cyan

$instance1 = "http://localhost:5001"
$instance2 = "http://localhost:5002"
$eureka    = "http://localhost:8761"

# TC-01
Write-Host "`n[TC-01] Baslangic durumu kontrol..." -ForegroundColor Yellow
Invoke-RestMethod "$instance1/actuator/health" | ConvertTo-Json
Invoke-RestMethod "$instance2/actuator/health" | ConvertTo-Json

# TC-02
Write-Host "`n[TC-02] Instance 2 DOWN yapiliyor..." -ForegroundColor Yellow
Invoke-RestMethod -Method Post "$instance2/api/v1/admin/health/break"
Start-Sleep -Seconds 2
Invoke-RestMethod "$instance2/actuator/health" | ConvertTo-Json

# TC-03
Write-Host "`n[TC-03] Eureka'nin DOWN'a cekmesi bekleniyor (20sn)..." -ForegroundColor Yellow
Start-Sleep -Seconds 20
Invoke-RestMethod "$eureka/eureka/apps/order-service" | Select-Xml "//instance" | ForEach-Object { Write-Host ($_.Node.instanceId + " -> " + $_.Node.status) }

# TC-04
Write-Host "`n[TC-04] Trafik sadece instance 1'e gitmeli (10 istek)..." -ForegroundColor Yellow
1..10 | ForEach-Object { Invoke-RestMethod "$instance1/api/v1"; Start-Sleep -Milliseconds 300 }

# TC-05
Write-Host "`n[TC-05] Instance 2 iyilestiriliyor..." -ForegroundColor Yellow
Invoke-RestMethod -Method Post "$instance2/api/v1/admin/health/fix"
Start-Sleep -Seconds 10
Invoke-RestMethod "$instance2/actuator/health" | ConvertTo-Json
Invoke-RestMethod "$eureka/eureka/apps/order-service" | Select-Xml "//instance" | ForEach-Object { Write-Host ($_.Node.instanceId + " -> " + $_.Node.status) }

Write-Host "`n=== SENARYO TAMAMLANDI ===" -ForegroundColor Green
```

Scripti kaydet ve çalıştır:
```powershell
.\test-eureka-health.ps1
```

---

## Konfigürasyon Referansı

### Eureka Server (`eurekaserver/src/main/resources/application.yml`)

| Ayar | Değer | Açıklama |
|---|---|---|
| `enable-self-preservation` | `false` | Test ortamı için; prod'da `true` |
| `eviction-interval-timer-in-ms` | `5000` | Sağlıksız instance kontrolü sıklığı |
| `response-cache-update-interval-ms` | `3000` | Registry cache güncelleme hızı |

### Order Service Client (`orderservice/src/main/resources/application.yml`)

| Ayar | Değer | Açıklama |
|---|---|---|
| `eureka.client.healthcheck.enabled` | `true` | Actuator → Eureka health entegrasyonu |
| `lease-renewal-interval-in-seconds` | `5` | Heartbeat sıklığı (prod: 30) |
| `lease-expiration-duration-in-seconds` | `15` | Heartbeat gelmezse düşme süresi (prod: 90) |
| `loadbalancer.health-check.interval` | `10s` | LoadBalancer sağlık kontrolü |
| `feign.loadbalancer.retry.enabled` | `true` | Feign retry aktif |
| `max-retries-on-next-service-instance` | `2` | Farklı instance'a max retry sayısı |

---

## Önemli Notlar

> ⚠️ **Production Uyarıları:**
> - `enable-self-preservation: false` sadece test/dev ortamında kullanın
> - `lease-renewal-interval-in-seconds: 5` prod'da `30` olmalı
> - `lease-expiration-duration-in-seconds: 15` prod'da `90` olmalı
> - `HealthToggleController` ve `SimulatedHealthIndicator` prod build'e dahil edilmemeli (profil ile izole edin)

> ✅ **Prod'a Taşırken:**
> - `@Profile("!prod")` ile simülasyon bean'lerini izole edin
> - Self-preservation'ı açık bırakın
> - Lease sürelerini Eureka önerilene (`30/90sn`) çekin

