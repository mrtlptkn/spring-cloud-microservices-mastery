# Spring Cloud Microservices Mastery

Bu proje, Spring Cloud ekosisteminin temel bileşenlerini gerçek dünya senaryoları üzerinde uygulayan eğitim amaçlı bir mikroservis mimarisidir. Servislerin birbirleriyle ilişkileri, çalıştırma sırası ve genel proje akışı bu rehberde adım adım anlatılmaktadır.

---

## Proje Yapısı

```text
spring-cloud-microservices-mastery/
├── src/
│   ├── config-server/       → Merkezi konfigürasyon sunucusu
│   ├── eureka-server/       → Servis keşif merkezi
│   ├── admin-server/        → Spring Boot Admin izleme paneli
│   ├── gateway/             → API ağ geçidi (rate limiter, circuit breaker, auth)
│   ├── order-service/       → Sipariş servisi
│   ├── product-service/     → Ürün servisi
│   └── saga-service/        → Dağıtık transaction orkestrasyonu
├── docs/docker/docker/
│   ├── kafka/               → Kafka + Zookeeper + PostgreSQL + Kafka UI
│   ├── redis/               → Redis + Redis Insight
│   ├── zipkin/              → Zipkin tracing sunucusu
│   ├── keycloak/            → OAuth2 kimlik doğrulama sunucusu
│   └── elk/                 → Elasticsearch + Logstash + Kibana
```

---

## Port Haritası

| Servis | Port | Tür | Açıklama |
|---|---|---|---|
| `config-server` | 8085 | Spring Boot | Merkezi konfigürasyon |
| `eureka-server` | 8761 | Spring Boot | Servis keşfi |
| `admin-server` | 8081 | Spring Boot | İzleme paneli |
| `gateway` | 8084 | Spring Boot (WebFlux) | API geçidi |
| `order-service` | 5001 | Spring Boot | Sipariş yönetimi |
| `product-service` | 5002 | Spring Boot | Ürün yönetimi |
| `saga-service` | 5020 | Spring Boot | Saga orkestrasyonu |
| Kafka Broker | 29092 | Docker | Mesaj kuyruğu |
| Kafka UI | 8088 | Docker | Kafka yönetim arayüzü |
| PostgreSQL | 5432 | Docker | Veritabanı |
| Redis | 6379 | Docker | Rate limiter önbelleği |
| Redis Insight | 5540 | Docker | Redis yönetim arayüzü |
| Zipkin | 9411 | Docker | Dağıtık iz sürme |
| Keycloak | 8180 | Docker | OAuth2 / OpenID Connect |

---

## Servisler Arası İlişki Haritası

Aşağıdaki diyagram servislerin birbirine olan bağımlılıklarını gösterir:

```text
                       ┌─────────────────┐
                       │  Config Server  │ :8085
                       └────────┬────────┘
                                │ konfigürasyon sağlar
              ┌─────────────────┼───────────────────┐
              ▼                 ▼                   ▼
    ┌──────────────┐   ┌─────────────┐    ┌───────────────┐
    │ order-service│   │  (diğer     │    │ eureka-server │ :8761
    │    :5001     │   │  servisler) │    └───────┬───────┘
    └──────┬───────┘   └─────────────┘            │ kayıt alır
           │                               ┌──────┘
           │ Eureka'dan keşif              ▼
           │ OpenFeign ile çağırır  ┌──────────────────┐
           ▼                        │ product-service  │ :5002
    ┌──────────────┐                └──────┬───────────┘
    │   gateway    │ :8084                 │
    │  (WebFlux)   │                       │ Kafka'ya event basar
    └──────┬───────┘               ┌───────┘
           │ Keycloak JWT doğrular ▼
           │           ┌────────────────┐
           │           │  Kafka Broker  │ :29092
           │           └───────┬────────┘
           │                   │ event consumer
           ▼                   ▼
    ┌──────────────┐   ┌──────────────────┐
    │   Keycloak   │   │  saga-service    │ :5020
    │    :8180     │   │  (orchestrator)  │
    └──────────────┘   └──────────────────┘
```

---

## Servis Bağımlılıkları (Özet)

| Servis | Neye bağımlı | Neden |
|---|---|---|
| `eureka-server` | — | Kimseye bağımlı değil, ilk başlar |
| `config-server` | — | Kimseye bağımlı değil, eureka ile eş zamanlı |
| `admin-server` | — | Opsiyonel; bağımsız çalışır |
| `gateway` | Eureka, Redis, Keycloak | Servis keşfi, rate limiter, JWT doğrulama |
| `order-service` | Config Server, Eureka, Kafka, Zipkin, Admin Server | Konfigürasyon, keşif, event üretimi |
| `product-service` | Eureka, Kafka, Admin Server | Keşif, event tüketimi |
| `saga-service` | Kafka, PostgreSQL | Event orkestrasyon, state kalıcılığı |

---

## Çalıştırma Sırası (Adım Adım)

### 1. Adım — Altyapıyı Başlat (Docker)

Tüm servislerin bağlandığı altyapı bileşenlerini Docker üzerinden başlatın.

#### Kafka + Zookeeper + PostgreSQL

```powershell
cd "docs\docker\docker\kafka"
docker-compose up -d
```

Bu komut şunları başlatır:
- Zookeeper (:2181)
- Kafka Broker (:29092)
- PostgreSQL (:5432)
- Debezium Connect (:8083)
- Kafka UI (:8088)

> **Not:** `saga-service` için `saga_db` veritabanını PostgreSQL içinde oluşturun:
> ```sql
> CREATE DATABASE saga_db;
> ```

#### Redis

```powershell
cd "..\redis"
docker-compose up -d
```

Bu komut şunları başlatır:
- Redis (:6379) — şifre: `Neominal`
- Redis Insight (:5540)

#### Zipkin (Opsiyonel)

```powershell
cd "..\zipkin"
docker-compose up -d
```

Zipkin (:9411) başlar.

#### Keycloak (Opsiyonel — Gateway JWT doğrulaması için)

```powershell
cd "..\keycloak"
docker-compose up -d
```

Keycloak (:8180) başlar.
- Admin kullanıcı: `admin` / `admin`
- Realm: `AuthServer` (gateway konfigürasyonunda tanımlı)

---

### 2. Adım — Eureka Server'ı Başlat

Eureka, servislerin birbirini bulabilmesi için ilk başlaması gereken Spring Boot uygulamasıdır. Diğer tüm Spring servisler başlarken Eureka'ya kayıt olmaya çalışır.

```powershell
cd "src\eureka-server"
.\mvnw.cmd spring-boot:run
```

Eureka UI: `http://localhost:8761`

---

### 3. Adım — Config Server'ı Başlat

`order-service`, başlarken Config Server'dan konfigürasyon çekmeye çalışır. Bu yüzden Config Server'ın `order-service`'ten önce ayakta olması gerekir.

```powershell
cd "src\config-server"
.\mvnw.cmd spring-boot:run
```

Config Server endpoint örneği: `http://localhost:8085/order-service/dev`

---

### 4. Adım — Admin Server'ı Başlat (Opsiyonel)

Spring Boot Admin, `order-service` ve `product-service`'in sağlık durumunu, loglarını ve metriklerini merkezi olarak sunar. Opsiyoneldir, diğer servisler olmadan da çalışabilir.

```powershell
cd "src\admin-server"
.\mvnw.cmd spring-boot:run
```

Admin UI: `http://localhost:8081`

---

### 5. Adım — Product Service'i Başlat

`product-service`, Eureka'ya kayıt olur ve `order_topic` Kafka topic'ini dinlemeye başlar. `order-service`'ten bağımsız çalışabilir; ancak event'leri işlemek için Kafka'nın ayakta olması gerekir.

```powershell
cd "src\product-service"
.\mvnw.cmd spring-boot:run
```

---

### 6. Adım — Order Service'i Başlat

`order-service`, Config Server'dan konfigürasyon alır ve Eureka üzerinden `product-service`'i bulur. Sipariş event'lerini Kafka'ya basar.

```powershell
cd "src\order-service"
.\mvnw.cmd spring-boot:run
```

---

### 7. Adım — Gateway'i Başlat

Gateway, Eureka'dan servis adreslerini okur ve gelen istekleri `order-service` ile `product-service`'e yönlendirir. Redis üzerinden rate limiter uygular; Keycloak üzerinden JWT doğrulaması yapar.

```powershell
cd "src\gateway"
.\mvnw.cmd spring-boot:run
```

Gateway üzerinden örnek istek:
- `http://localhost:8084/order-service/api/v1/...`
- `http://localhost:8084/product-service/api/v1/...`

---

### 8. Adım — Saga Service'i Başlat (Opsiyonel)

`saga-service`, Kafka topic'lerini dinler ve dağıtık sipariş akışını adım adım orkestrate eder. PostgreSQL'e state kaydeder.

```powershell
cd "src\saga-service"
.\mvnw.cmd spring-boot:run
```

Saga başlatma endpoint'i:
```
POST http://localhost:5020/api/v1/saga/submit
```

---

## Genel Akış

### Order-Service → Product-Service (Senkron, OpenFeign)

```text
İstemci → Gateway (JWT doğrula, route et)
       → order-service (sipariş oluştur)
       → product-service (OpenFeign ile ürün detayı çek)
       ← cevap döner
```

### Order-Service → Product-Service (Asenkron, Kafka)

```text
order-service → order_topic → product-service (stok kontrol)
product-service → [başarısız ise] → order_failed_topic → order-service (geri al)
```

### Saga Orkestrasyon Akışı

```text
POST /api/v1/saga/submit
  → saga-service (OrderSubmittedEvent)
  → saga_order_checkStock → inventory adımı
  → saga_order_stockReserved / saga_order_stockNotAvailable
  → [stok varsa] saga_order_makePayment → ödeme adımı
  → saga_order_paidSucceeded / saga_order_paidFailed
  → [başarılıysa] saga_order_completeOrder → sipariş tamamlandı
  → [başarısızsa] saga_order_releaseStock + saga_order_rejectOrder → telafi
```

---

## Kafka Topic Haritası

| Topic | Üretici | Tüketici | Açıklama |
|---|---|---|---|
| `order_topic` | order-service | product-service | Sipariş oluşturma event'i |
| `order_failed_topic` | product-service | order-service | Stok yetersiz telafi event'i |
| `order_dlq_topic` | (DLQ) | — | Başarısız mesajların dead-letter kuyruğu |
| `saga_order_submitted` | saga-service | saga-service | Saga başlangıcı |
| `saga_order_checkStock` | saga-service | inventory | Stok kontrolü |
| `saga_order_stockReserved` | inventory | saga-service | Stok ayrıldı |
| `saga_order_stockNotAvailable` | inventory | saga-service | Stok yok |
| `saga_order_makePayment` | saga-service | payment | Ödeme talebi |
| `saga_order_paidSucceeded` | payment | saga-service | Ödeme başarılı |
| `saga_order_paidFailed` | payment | saga-service | Ödeme başarısız |
| `saga_order_completeOrder` | saga-service | order-service | Sipariş tamamlandı |
| `saga_order_rejectOrder` | saga-service | order-service | Sipariş reddedildi |
| `saga_order_releaseStock` | saga-service | inventory | Stok geri bırak (telafi) |

---

## Gateway Yönlendirme ve Güvenlik

Gateway tüm dış isteklerin giriş noktasıdır.

| Rota | Hedef | Filtre |
|---|---|---|
| `/order-service/api/v1/**` | `order-service` | Kimlik doğrulama |
| `/product-service/api/v1/**` | `product-service` | Rate Limiter (Redis, IP bazlı), Circuit Breaker |

### Rate Limiter Ayarı

```yaml
redis-rate-limiter.replenishRate: 1   # saniyede 1 istek hakkı
redis-rate-limiter.burstCapacity: 1   # biriken hak üst sınırı: 1
```

### Circuit Breaker

- Hata eşiği: son 3 istek, minimum 3 çağrı
- Açık kalma süresi: 30 saniye
- Fallback: `/fallback/product-service`

### Keycloak JWT

- Issuer URI: `http://localhost:8189/realms/AuthServer`
- JWKS URI: `http://localhost:8189/realms/AuthServer/protocol/openid-connect/certs`

---

## Gözlemlenebilirlik

| Araç | URL | Amaç |
|---|---|---|
| Eureka UI | `http://localhost:8761` | Kayıtlı servisleri görüntüle |
| Spring Boot Admin | `http://localhost:8081` | Servis sağlığı, loglar, metrikler |
| Zipkin | `http://localhost:9411` | Dağıtık istek izleme |
| Kafka UI | `http://localhost:8088` | Topic ve mesaj izleme |
| Redis Insight | `http://localhost:5540` | Redis önbellek durumu |
| Keycloak Admin | `http://localhost:8180` | Realm, kullanıcı ve token yönetimi |

---

## Servis Bazlı Başlatma Önceliği (Özet)

```text
ÖNCE (altyapı — Docker)
  1. Kafka + Zookeeper + PostgreSQL
  2. Redis
  3. Zipkin (opsiyonel)
  4. Keycloak (opsiyonel)

SONRA (Spring Boot — sırayla)
  5. Eureka Server
  6. Config Server
  7. Admin Server (opsiyonel)
  8. Product Service
  9. Order Service
  10. Gateway
  11. Saga Service (opsiyonel / bağımsız)
```

---

## Servis Başlatılmadan Olacaklar

| Servis başlamadan | Etki |
|---|---|
| Eureka çalışmıyorsa | Gateway, order-service ve product-service birbirini bulamaz; OpenFeign çağrıları başarısız olur |
| Config Server çalışmıyorsa | order-service konfigürasyonunu çekemez; ancak `optional:` ile işaretli olduğundan başlayabilir |
| Kafka çalışmıyorsa | order-service event üretimi başarısız olur; product-service consumer aktif olmaz; saga-service çalışmaz |
| Redis çalışmıyorsa | Gateway rate limiter devreye giremez; product-service rotaları yanıt veremeyebilir |
| PostgreSQL çalışmıyorsa | saga-service başlayamaz (state kaydı için zorunlu) |
| Keycloak çalışmıyorsa | Gateway JWT doğrulaması başarısız olur; güvenli endpoint'lere erişim reddedilir |
| Zipkin çalışmıyorsa | Tracing logu gönderilemez; uygulama çalışmaya devam eder ancak dağıtık iz sunamaz |

---

## Hızlı Başlangıç (Tüm Hizmetler)

```powershell
# 1. Altyapı
cd "docs\docker\docker\kafka"  ; docker-compose up -d
cd "..\redis"                  ; docker-compose up -d
cd "..\zipkin"                 ; docker-compose up -d

# 2. Eureka
cd "..\..\..\..\src\eureka-server" ; .\mvnw.cmd spring-boot:run

# 3. Config Server
cd "..\config-server"              ; .\mvnw.cmd spring-boot:run

# 4. Admin Server
cd "..\admin-server"               ; .\mvnw.cmd spring-boot:run

# 5. Product Service
cd "..\product-service"            ; .\mvnw.cmd spring-boot:run

# 6. Order Service
cd "..\order-service"              ; .\mvnw.cmd spring-boot:run

# 7. Gateway
cd "..\gateway"                    ; .\mvnw.cmd spring-boot:run

# 8. Saga Service
cd "..\saga-service"               ; .\mvnw.cmd spring-boot:run
```

> Her servis için ayrı bir terminal/PowerShell penceresi açmanız önerilir.

---

## Notlar

- Bu proje geliştirme ve öğrenme amaçlıdır; production konfigürasyonları her servisin kendi `application-prod.yml` dosyasında ayrıca tanımlanmıştır.
- Saga servisi `saga-service` içindeki inventory ve payment adımları simülasyon amacıyla `Math.random()` ile çalışır; gerçek ortamda ayrı servisler olması gerekir.
- `order-service`, Config Server'dan `optional:` ile konfigürasyon çektiğinden Config Server ayakta olmasa bile çalışmaya devam edebilir.
- Tüm servisler için Türkçe karakter seti UTF-8 ile korunmaktadır.

