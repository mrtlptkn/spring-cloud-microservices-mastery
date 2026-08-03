# LoadBalancer Çoklu Instance Senaryosu — FEIGN_TARGET Log ile Doğrulama

> **Amaç:** `product-service`'i 3 farklı portta (5002, 5003, 5004) çalıştırarak
> Spring Cloud LoadBalancer'ın istekleri nasıl dağıttığını `[FEIGN_TARGET]` logları
> üzerinden gözlemlemek.  
> İstekler **Gateway** (`localhost:8084`) üzerinden `product-service` endpointine gider,
> `order-service` logunda hangi porta yönlendirildiği görülür.

---

## Mimari

```
                           ┌─────────────────────────────────────────┐
[Client / Postman]         │         Spring Cloud LoadBalancer        │
  POST :8084               │                                          │
  /product-service/        │   Eureka'dan 3 instance alır:            │
  api/v1/products/details  │   ├── product-service:5002 (UP)          │
         │                 │   ├── product-service:5003 (UP)          │
         ▼                 │   └── product-service:5004 (UP)          │
  [Gateway :8084]          │                                          │
  lb://product-service ───▶│   Round-Robin ile seçer                  │
                           └─────────────────────────────────────────┘
                                           │
                              ┌────────────┼────────────┐
                              ▼            ▼            ▼
                       [:5002]       [:5003]       [:5004]
                    product-service  product-service  product-service
                    log: port=5002   log: port=5003   log: port=5004
```

---

## Hangi Kod Hangi Logu Üretiyor?

### `OrderController` — `[FEIGN_TARGET]` logu

`order-service` her istek öncesinde `LoadBalancerClient.choose()` ile seçilen instance'ı loglar:

```java
// OrderController.java — getOrderedProductDetails()
ServiceInstance selectedInstance = loadBalancerClient.choose("product-service");
if (selectedInstance != null) {
    log.warn("[FEIGN_TARGET] service={} host={} port={} uri={}",
            selectedInstance.getServiceId(),
            selectedInstance.getHost(),
            selectedInstance.getPort(),
            selectedInstance.getUri());
}
```

**Örnek log çıktısı (order-service konsolu):**
```
WARN  [FEIGN_TARGET] service=product-service host=192.168.1.10 port=5002 uri=http://192.168.1.10:5002
WARN  [FEIGN_TARGET] service=product-service host=192.168.1.10 port=5003 uri=http://192.168.1.10:5003
WARN  [FEIGN_TARGET] service=product-service host=192.168.1.10 port=5004 uri=http://192.168.1.10:5004
```

### `ProductsController` — port logu

Her `product-service` instance'ı kendi portunu loglar:

```java
// ProductsController.java
log.info("product-request" + serverPort);
// → "product-request5002" / "product-request5003" / "product-request5004"
```

---

## Ön Koşullar

Aşağıdaki servisler **bu sırayla** çalışıyor olmalıdır:

| Servis         | Port | Başlatma Klasörü         |
|----------------|------|--------------------------|
| Eureka Server  | 8761 | `src/eurekaserver`       |
| Gateway        | 8084 | `src/gateway`            |
| Order Service  | 5001 | `src/orderservice`       |
| Product Service (×3) | 5002, 5003, 5004 | `src/productservice` |

> ⚠️ Gateway varsayılan olarak `keycloak` profiliyle başlıyor.
> Keycloak olmadan test etmek için `public` profiliyle başlat:
> ```powershell
> $env:SPRING_PROFILES_ACTIVE="public"; .\mvnw.cmd spring-boot:run
> ```

---

## Product Service'i 3 Farklı Portta Başlatma

Her terminal `src/productservice` klasöründe açılmalıdır.

### Terminal-1 — Port 5002
```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\productservice"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=5002 --logging.file.name=./logs/product-service/product-service-5002.log"
```

### Terminal-2 — Port 5003
```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\productservice"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=5003 --logging.file.name=./logs/product-service/product-service-5003.log"
```

### Terminal-3 — Port 5004
```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\productservice"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=5004 --logging.file.name=./logs/product-service/product-service-5004.log"
```

---

## Eureka'da Instance'ları Doğrula

```
http://localhost:8761
```

Beklenen görünüm:
```
PRODUCT-SERVICE   product-service:5002   UP
PRODUCT-SERVICE   product-service:5003   UP
PRODUCT-SERVICE   product-service:5004   UP
```

API ile doğrulama:
```powershell
Invoke-RestMethod "http://localhost:8761/eureka/apps/product-service" `
  -Headers @{Accept="application/json"} | ConvertTo-Json -Depth 5
```

---

## Test Case'leri

---

### TC-01: Gateway Üzerinden Direkt product-service Endpoint'i

**Amaç:** Gateway'in `lb://product-service` üzerinden yük dağıttığını doğrulamak.

**Endpoint:** `POST http://localhost:8084/product-service/api/v1/products/details`

**Request Body:**
```json
{
  "productIds": ["P-001"]
}
```

**PowerShell:**
```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8084/product-service/api/v1/products/details" `
  -ContentType "application/json" `
  -Body '{"productIds":["P-001"]}'
```

**Beklenen Response:**
```json
{
  "orderedProducts": [
    { "productId": "P-1", "price": 100.2, "quantity": 10 },
    { "productId": "P-2", "price": 100.2, "quantity": 10 }
  ]
}
```

**Hangi instance karşıladı? → product-service loguna bak:**
```powershell
Get-Content ".\logs\product-service\product-service-5002.log" | Select-String "product-request"
Get-Content ".\logs\product-service\product-service-5003.log" | Select-String "product-request"
Get-Content ".\logs\product-service\product-service-5004.log" | Select-String "product-request"
```

---

### TC-02: order-service Üzerinden [FEIGN_TARGET] Logunu Gözlemle

**Amaç:** `order-service`'in Feign ile `product-service`'e giderken
`LoadBalancerClient`'ın seçtiği instance'ı `[FEIGN_TARGET]` logu olarak görmek.

**Endpoint:** `GET http://localhost:5001/api/v1/orders/ORD-001/orderDetails`

```powershell
Invoke-RestMethod "http://localhost:5001/api/v1/orders/ORD-001/orderDetails"
```

**order-service konsolunda beklenen log:**
```
WARN  [FEIGN_TARGET] service=product-service host=192.168.x.x port=5002 uri=http://192.168.x.x:5002
INFO  order-service-request
```

---

### TC-03: Round-Robin Dağılımını Doğrula (10 İstek)

**Amaç:** Ardışık 10 istekte LoadBalancer'ın 3 instance'a round-robin ile dağıttığını görmek.

```powershell
1..10 | ForEach-Object {
    Invoke-RestMethod -Method Post `
        -Uri "http://localhost:8084/product-service/api/v1/products/details" `
        -ContentType "application/json" `
        -Body '{"productIds":["P-001"]}'
    Write-Host "[$_] istek gonderildi"
    Start-Sleep -Milliseconds 300
}
```

**order-service konsolunda beklenen sıra:**
```
[FEIGN_TARGET] ... port=5002
[FEIGN_TARGET] ... port=5003
[FEIGN_TARGET] ... port=5004
[FEIGN_TARGET] ... port=5002   ← döngü başladı
[FEIGN_TARGET] ... port=5003
[FEIGN_TARGET] ... port=5004
...
```

> 💡 Spring Cloud LoadBalancer varsayılan olarak **`RoundRobinLoadBalancer`** kullanır.
> Her çağrıda sırayla bir sonraki instance seçilir.

---

### TC-04: Log Dosyalarına Göre Kaç İstek Hangi Instance'a Gitti

10 istek sonrası her log dosyasındaki `product-request` satır sayısını say:

```powershell
$c1 = (Get-Content ".\logs\product-service\product-service-5002.log" | Select-String "product-request").Count
$c2 = (Get-Content ".\logs\product-service\product-service-5003.log" | Select-String "product-request").Count
$c3 = (Get-Content ".\logs\product-service\product-service-5004.log" | Select-String "product-request").Count
Write-Host "5002: $c1 istek | 5003: $c2 istek | 5004: $c3 istek"
```

**Beklenen (10 istek / 3 instance, round-robin):**
```
5002: 4 istek | 5003: 3 istek | 5004: 3 istek
```

---

### TC-05: Gateway → order-service → product-service Tam Zincir

**Amaç:** Gateway → order-service → product-service zincirinin tamamını doğrulamak.

```powershell
Invoke-RestMethod "http://localhost:8084/order-service/api/v1/orders/ORD-100/orderDetails"
```

**Tam akış:**
```
Client
  → Gateway:8084  (lb://order-service → order-service:5001)
      → order-service:5001  [FEIGN_TARGET logu yazılır]
          → Feign → product-service:500X  (LoadBalancer seçer)
              → product-request500X logu yazılır
```

**order-service konsolunda:**
```
INFO  order-service-request
WARN  [FEIGN_TARGET] service=product-service host=x.x.x.x port=5003 uri=http://...
```

---

### TC-06: Bir Instance'ı Durdur — LoadBalancer Sağlıklı Instance'a Yönleniyor mu?

**Amaç:** 3 instance'tan birini durdurduğumuzda LoadBalancer'ın
kalan 2 instance'a yönlendirdiğini doğrulamak.

**Adımlar:**
1. Terminal-3'ü kapat → port 5004 durur
2. Eureka'nın 5004'ü `DOWN` olarak işaretlemesini bekle (~15-20sn)
3. İstekleri tekrar at

```powershell
# 5004 durduruldu, Eureka güncellemesini bekle
Start-Sleep -Seconds 20

# 6 istek at — sadece 5002 ve 5003 arasında dönmeli
1..6 | ForEach-Object {
    Invoke-RestMethod -Method Post `
        -Uri "http://localhost:8084/product-service/api/v1/products/details" `
        -ContentType "application/json" `
        -Body '{"productIds":["P-001"]}'
    Write-Host "[$_] OK"
    Start-Sleep -Milliseconds 300
}
```

**order-service konsolunda beklenen:**
```
[FEIGN_TARGET] ... port=5002
[FEIGN_TARGET] ... port=5003
[FEIGN_TARGET] ... port=5002
[FEIGN_TARGET] ... port=5003
# 5004 hiç görünmemeli ✅
```

---

### TC-07: Her Instance'a Paralel İstek Gönder

**Amaç:** 3 instance'ın aynı anda kendi üzerine gelen istekleri işlediğini görmek.

```powershell
$j1 = Start-Job { Invoke-RestMethod -Method Post -Uri "http://localhost:5002/api/v1/products/details" -ContentType "application/json" -Body '{"productIds":["P-001"]}' }
$j2 = Start-Job { Invoke-RestMethod -Method Post -Uri "http://localhost:5003/api/v1/products/details" -ContentType "application/json" -Body '{"productIds":["P-001"]}' }
$j3 = Start-Job { Invoke-RestMethod -Method Post -Uri "http://localhost:5004/api/v1/products/details" -ContentType "application/json" -Body '{"productIds":["P-001"]}' }
Wait-Job $j1,$j2,$j3
Receive-Job $j1,$j2,$j3
```

**Her log dosyasında 1'er satır görünmeli:**
```
product-service-5002.log → product-request5002
product-service-5003.log → product-request5003
product-service-5004.log → product-request5004
```

---

## Canlı Log Takibi (Real-Time)

3 log dosyasını aynı anda izlemek için:

```powershell
Start-Job { Get-Content ".\logs\product-service\product-service-5002.log" -Wait | ForEach-Object { Write-Host "[5002] $_" } }
Start-Job { Get-Content ".\logs\product-service\product-service-5003.log" -Wait | ForEach-Object { Write-Host "[5003] $_" } }
Start-Job { Get-Content ".\logs\product-service\product-service-5004.log" -Wait | ForEach-Object { Write-Host "[5004] $_" } }
```

---

## Port & URL Referans Tablosu

| Servis | Port | Direkt Erişim | Gateway Üzerinden |
|--------|------|---------------|-------------------|
| Gateway | 8084 | — | — |
| Eureka | 8761 | `http://localhost:8761` | — |
| order-service | 5001 | `localhost:5001/api/v1/orders/...` | `localhost:8084/order-service/api/v1/orders/...` |
| product-service #1 | 5002 | `localhost:5002/api/v1/products/details` | `localhost:8084/product-service/api/v1/products/details` |
| product-service #2 | 5003 | `localhost:5003/api/v1/products/details` | `localhost:8084/product-service/api/v1/products/details` |
| product-service #3 | 5004 | `localhost:5004/api/v1/products/details` | `localhost:8084/product-service/api/v1/products/details` |

---

## Özet: Gözlemlenecek Davranışlar

| # | Gözlem | Nerede İzlenir |
|---|--------|----------------|
| 1 | Her istekte farklı port seçiliyor | `order-service` konsol — `[FEIGN_TARGET]` |
| 2 | Round-robin sırası: 5002 → 5003 → 5004 → 5002... | `order-service` konsol |
| 3 | Her instance kendi isteğini logluyor | `product-service-500X.log` |
| 4 | Instance kapanınca trafik kalan 2'ye geçiyor | `order-service` konsol |
| 5 | Eureka'da 3 UP instance görünüyor | `http://localhost:8761` |
| 6 | Gateway `lb://product-service` ile dağıtıyor | Gateway konsol (DEBUG seviyesinde) |

