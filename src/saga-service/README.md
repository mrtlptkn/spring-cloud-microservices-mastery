# Saga Service

Bu modül, mikroservis mimarisinde **Saga Orchestration** yaklaşımıyla sipariş sürecinin adım adım yönetilmesini sağlar.
`saga-service`; siparişin gönderilmesi, stok kontrolü, ödeme işlemi, başarılı/başarısız senaryolar ve telafi (compensation) adımlarını event tabanlı olarak koordine eder.

## Amaç

`saga-service` projesinin temel hedefleri:

- Dağıtık transaction sürecini merkezi bir orkestrasyonla yönetmek
- Sipariş akışını event-driven mimaride adımlara bölmek
- Başarısız adımlarda telafi event’leri üretmek
- Süreçteki state geçişlerini veritabanında izlemek
- Order, inventory ve payment adımlarını gevşek bağlı şekilde yönetmek

## Kullanılan Sürümler

`pom.xml` dosyasına göre:

- **Java:** 17
- **Spring Boot:** 3.5.6
- **Spring Cloud:** 2025.0.0
- **Build Tool:** Maven

## Kullanılan Teknolojiler

- **Spring Boot Web**: REST endpoint sunumu
- **Spring Cloud Stream + Kafka**: Event publish/consume
- **Spring Cloud Function**: Consumer fonksiyonları
- **Spring Boot Data JPA**: Saga state kalıcılığı
- **PostgreSQL Driver**: Runtime veritabanı sürücüsü
- **Lombok**: Boilerplate azaltma
- **Spring Boot Test + Stream Test Binder**: Test desteği

## Bağımlılıklar

Ana bağımlılıklar (`pom.xml`):

- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.cloud:spring-cloud-starter-stream-kafka`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `org.postgresql:postgresql`
- `org.projectlombok:lombok`
- `org.springframework.cloud:spring-cloud-stream-test-binder` (test)

## Mimari Yaklaşım

Bu servis **Orchestration-based Saga** yaklaşımı uygular.

- Orchestrator: `saga-service`
- Katılımcılar: `order-service`, `inventory-service`, `payment-service`
- İletişim: Kafka topic’leri üzerinden asenkron event akışı

Bu modelde her adım bağımsızdır; herhangi bir adım başarısız olduğunda işlem geri alma (compensating transaction) event’leri tetiklenir.

## Proje Yapısı

- `controller/SagaController.java` → Saga başlatma endpoint’i
- `service/OrderSagaService.java` → Event yayınlama (producer)
- `service/OrderSagaConsumer.java` → Event tüketme (consumer)
- `service/OrderSagaHandler.java` → Saga adım kararları ve state geçişleri
- `entity/OrderSagaState.java` → Saga adım kayıtları
- `repository/IOrderSagaRepository.java` → Saga state repository
- `event/*` → Saga event sözleşmeleri (record)

## API Uç Noktası

### `POST /api/v1/saga/submit`

Saga akışını başlatır ve ilk event’i yayınlar.

**Request örneği:**

```json
{
  "orderId": "ORD-1001",
  "code": "P-1",
  "quantity": 2
}
```

Bu istekten sonra servis `orderSubmitEvent-out-0` binding’i üzerinden `saga_order_submitted` topic’ine event gönderir.

## Saga Yaşam Döngüsü

### Başarılı Senaryo

1. `OrderSubmittedEvent` alınır
2. `CheckStockEvent` gönderilir
3. `StockReservedEvent` alınır
4. `MakePaymentEvent` gönderilir
5. `PaidSucceededEvent` alınır
6. `CompleteOrderEvent` gönderilir

### Başarısız Senaryolar

**Stok yoksa:**

1. `OrderSubmittedEvent`
2. `CheckStockEvent`
3. `StockNotAvailableEvent`
4. `RejectOrderEvent`

**Ödeme başarısızsa (compensation):**

1. `OrderSubmittedEvent`
2. `CheckStockEvent`
3. `StockReservedEvent`
4. `MakePaymentEvent`
5. `PaidFailedEvent`
6. `ReleaseStockEvent` (telafi)
7. `RejectOrderEvent`

## Event Akışı (Özet Diyagram)

```text
Order Service -> saga_order_submitted -> Saga Service
Saga Service  -> saga_order_checkStock -> Inventory Service
Inventory     -> saga_order_stockReserved / saga_order_stockNotAvailable -> Saga Service
Saga Service  -> saga_order_makePayment -> Payment Service
Payment       -> saga_order_paidSucceeded / saga_order_paidFailed -> Saga Service
Saga Service  -> saga_order_completeOrder / saga_order_rejectOrder -> Order Service
Saga Service  -> saga_order_releaseStock -> Inventory Service
```

## Event Sözleşmeleri

Kodda tanımlı record yapıları:

- `OrderSubmittedEvent(orderId, code, quantity)`
- `CheckStockEvent(orderId, code, quantity)`
- `StockReservedEvent(orderId, code, quantity)`
- `StockNotAvailableEvent(orderId, code)`
- `MakePaymentEvent(orderId, code, amount)`
- `PaidSucceededEvent(orderId, message)`
- `PaidFailedEvent(orderId, code, message)`
- `CompleteOrderEvent(orderId)`
- `RejectOrderEvent(orderId, reason)`
- `ReleaseStockEvent(code, orderId)`

## State Yönetimi ve Kalıcılık

Saga adım geçişleri `OrderSagaHandler` içinde `OrderSagaState` tablosuna yazılır.

`OrderSagaState` alanları:

- `id`
- `orderId`
- `status`
- `reason`
- `createdAt`

Bu kayıtlar sayesinde bir siparişin hangi adımlardan geçtiği, hangi adımda başarısız olduğu ve telafi adımına gidip gitmediği izlenebilir.

## Konfigürasyon

### `application.yml`

Temel ayarlar:

- **Port:** `5020`
- **Uygulama adı:** `saga-service`
- **DB:** `jdbc:postgresql://localhost:5432/saga_db`
- **Kafka broker:** `localhost:29092`
- **Topic auto-create:** `true` (geliştirme için)
- **Idempotence:** `true`
- **ACKs:** `all`
- **Retries:** `10`

### Function Tanımı

```yaml
spring:
  cloud:
    function:
      definition: orderSubmitEvent;checkStockEvent;makePaymentEvent;stockReservedEvent;stockNotAvailableEvent;paidSucceededEvent;paidFailedEvent
```

Bu tanım, Spring Cloud Function tarafında hangi consumer bean’lerinin aktif olacağını belirler.

### Binding Örnekleri

```yaml
spring:
  cloud:
    stream:
      bindings:
        orderSubmitEvent-out-0:
          destination: saga_order_submitted
        orderSubmitEvent-in-0:
          destination: saga_order_submitted
          group: saga-service
```

## Topic ve Binding Matrisi

| Binding | Direction | Topic | Group | Açıklama |
|---|---|---|---|---|
| `orderSubmitEvent-out-0` | Out | `saga_order_submitted` | - | Saga başlangıç event’i |
| `checkStockEvent-out-0` | Out | `saga_order_checkStock` | - | Stok kontrol talebi |
| `makePaymentEvent-out-0` | Out | `saga_order_makePayment` | - | Ödeme talebi |
| `stockReservedEvent-out-0` | Out | `saga_order_stockReserved` | - | Stok ayrıldı bildirimi |
| `stockNotAvailableEvent-out-0` | Out | `saga_order_stockNotAvailable` | - | Stok yetersiz bildirimi |
| `paidSucceededEvent-out-0` | Out | `saga_order_paidSucceeded` | - | Ödeme başarılı bildirimi |
| `paidFailedEvent-out-0` | Out | `saga_order_paidFailed` | - | Ödeme başarısız bildirimi |
| `completeOrderEvent-out-0` | Out | `saga_order_completeOrder` | - | Sipariş tamamla |
| `rejectOrderEvent-out-0` | Out | `saga_order_rejectOrder` | - | Sipariş reddet |
| `releaseStockEvent-out-0` | Out | `saga_order_releaseStock` | - | Stok geri bırak |

## Production İçin Önemli Ayarlar

### 1) Kafka Topic Yönetimi

Geliştirme ortamında `auto-create-topics: true` hızlı başlangıç sağlar; ancak production’da önerilmez.

Production önerisi:

- `auto-create-topics: false`
- Topic’leri önceden oluştur
- `replication-factor`, `partitions`, `retention.ms`, `min.insync.replicas` değerlerini açık belirle

### 2) Güvenilir Mesajlaşma

- `enable.idempotence: true`
- `acks: all`
- `retries: 10`
- `max.in.flight.requests.per.connection` kontrollü tutulmalı
- Tüketici tarafında DLQ/retry stratejisi net tanımlanmalı

### 3) Veritabanı Yönetimi

- `ddl-auto: update` geliştirme için uygundur
- Production’da migration aracıyla (`Flyway`/`Liquibase`) şema yönetimi önerilir
- Connection pool ayarları (`Hikari`) netleştirilmelidir

### 4) Gözlemlenebilirlik ve Loglama

Mevcut yapı `logging.level.root: OFF` içerir. Production’da bu ayar genellikle önerilmez.

Öneri:

```yaml
logging:
  level:
    root: INFO
    com.mertalptekin.sagaservice: INFO
```

Ayrıca merkezi log yönetimi için ELK/OpenSearch entegrasyonu eklenebilir.

## Docker ve Ortam Gereksinimleri

Çalışma için asgari gereksinimler:

- Kafka Broker
- PostgreSQL

Projedeki mevcut altyapı dosyaları:

- `docs/docker/docker/kafka/docker-compose.yml`

Not: Bu compose içinde PostgreSQL bulunduğu durumda `saga_db` veritabanının oluşturulmuş olması gerekir.

## Çalıştırma

### 1) Altyapıyı başlat

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\docs\docker\docker\kafka"
docker-compose up -d
```

### 2) Uygulamayı başlat

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\saga-service"
.\mvnw.cmd spring-boot:run
```

### 3) Saga başlatma örneği

```powershell
curl -Method Post "http://localhost:5020/api/v1/saga/submit" -Headers @{"Content-Type"="application/json"} -Body '{"orderId":"ORD-1001","code":"P-1","quantity":2}'
```

## Test

Mevcut test sınıfı:

- `src/test/java/com/mertalptekin/sagaservice/SagaServiceApplicationTests.java`

Test çalıştırma:

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\saga-service"
.\mvnw.cmd test
```

## Troubleshooting

### `Connection refused` (Kafka)

- `localhost:29092` adresinde broker çalışıyor mu kontrol et
- Docker compose servislerini doğrula

### PostgreSQL bağlantı hatası

- `saga_db` veritabanı var mı kontrol et
- `username/password` (`postgres/postgres`) bilgilerini doğrula

### Mesaj akışı ilerlemiyor

- Topic isimleri ile binding isimlerinin eşleştiğini kontrol et
- `function.definition` içinde ilgili consumer bean adları doğru mu kontrol et

### Saga state kayıtları oluşmuyor

- `OrderSagaHandler` akışına event düşüp düşmediğini loglardan doğrula
- JPA tablo adı `order-saga-states` oluşturulmuş mu kontrol et

## Geliştirme Notları

- `OrderSagaConsumer` içinde stok ve ödeme simülasyonu `Math.random()` ile yapılır; bu yapı demo amaçlıdır.
- Production’da inventory/payment doğrulaması gerçek servislerden gelmelidir.
- `IOrderSagaRepository` generic ID tipi ile `OrderSagaState.id` tipi production öncesi hizalanmalıdır.

## Notlar

- Bu servis, distributed transaction problemini local transaction’lara bölerek yönetir.
- Event’ler immutable record yapılarıyla taşındığı için sözleşme yönetimi önemlidir.
- Saga akışı, idempotent ve tekrar çalıştırılabilir olacak şekilde tasarlanmalıdır.

