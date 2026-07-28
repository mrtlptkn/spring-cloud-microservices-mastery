# Product Service

`product-service`, sipariş akışında ürün bilgilerini sağlayan ve sipariş etkinliklerini işleyen mikroservistir. Bu modül; ürün detaylarını döndürür, sipariş oluşturma event’lerini tüketir ve gerektiğinde telafi (compensation) event’leri üretir.

## Amaç

Bu servisin temel sorumlulukları:

- Sipariş için gerekli ürün detaylarını sağlamak
- Kafka üzerinden `order_topic` event’lerini tüketmek
- İş kuralı ihlallerinde telafi event’i üretmek
- Eureka ile servis keşfine katılmak
- Spring Boot Admin üzerinden izlenmek
- Spring Cloud Stream + Kafka ile event-driven mimaride çalışmak

## Kullanılan Sürümler

`pom.xml` dosyasına göre:

- **Java:** 17
- **Spring Boot:** 3.5.9
- **Spring Cloud:** 2025.0.1
- **Spring Boot Admin:** 3.5.6
- **Build Tool:** Maven

## Kullanılan Teknolojiler

- **Spring Boot Web**: REST API geliştirme
- **Spring Cloud Eureka Client**: Servis keşfi
- **Spring Cloud LoadBalancer**: İstemci taraflı yük dengeleme
- **Spring Cloud Stream + Kafka**: Event tüketimi ve üretimi
- **Spring Cloud Function**: Fonksiyon tabanlı consumer tanımı
- **Spring Boot Admin Client**: Merkezi izleme
- **OpenFeign**: Projede hazır durumda bulunan declarative HTTP client altyapısı
- **Spring Boot AOP**: Kesitsel loglama/izleme ihtiyaçları için altyapı
- **Micrometer Tracing**: Trace/span context yönetimi
- **Logback + Logstash Encoder**: JSON log üretimi ve ELK pipeline entegrasyonu
- **Lombok**: Boilerplate azaltma
- **Spring Boot Test**: Test desteği

## Bağımlılıklar

`product-service` içinde yer alan ana bağımlılıklar:

- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.cloud:spring-cloud-starter-netflix-eureka-client`
- `org.springframework.cloud:spring-cloud-starter-loadbalancer`
- `org.springframework.cloud:spring-cloud-starter-stream-kafka`
- `org.springframework.cloud:spring-cloud-starter-openfeign`
- `de.codecentric:spring-boot-admin-starter-client`
- `org.springframework.boot:spring-boot-starter-aop`
- `io.micrometer:micrometer-tracing`
- `net.logstash.logback:logstash-logback-encoder`
- `org.projectlombok:lombok`

> Not: Bu servis şu an Config Server kullanmıyor. Ortam bazlı ayarlar doğrudan `application.yml` ve `application-prod.yml` içinde tutuluyor.

## Proje Yapısı

- `controller/ProductsController.java` → Ürün detay API’si
- `consumer/SubmitOrderConsumer.java` → Kafka consumer ve telafi event üretimi
- `dto/OrderedProduct.java` → Ürün DTO’su
- `event/SubmitOrderEvent.java` → Sipariş oluşturma event’i
- `event/SubmitOrderFailedEvent.java` → Telafi event’i
- `request/OrderedProductDetailRequest.java` → Ürün detay istek modeli
- `response/OrderedProductDetailResponse.java` → Ürün detay cevap modeli

## Uç Noktalar

### `POST /api/v1/products/details`

Sipariş ekranında kullanılacak ürün detaylarını döndürür.

**Request örneği:**

```json
{
  "ProductIds": ["P-1", "P-2"]
}
```

**Response örneği:**

```json
{
  "orderedProducts": [
    {
      "productId": "P-1",
      "price": 100.2,
      "stock": 10
    },
    {
      "productId": "P-2",
      "price": 100.2,
      "stock": 10
    }
  ]
}
```

### Demo davranışı

`ProductsController` içinde senaryo amaçlı iki durum vardır:

- `ProductIds.length == 2` ise bilinçli olarak hata fırlatılır.
- `ProductIds.length == 3` ise 3 saniyelik gecikme simüle edilir.

Bu yapı, hata yönetimi ve gecikme toleransı senaryolarını test etmek için kullanılır.

## Event Akışı

`product-service` event-driven mimaride iki temel rol üstlenir:

1. **Consume**: `order_topic` üzerinden gelen sipariş event’lerini `orderSubmitted` fonksiyonu ile dinler.
2. **Compensate**: İş kuralı ihlali oluşursa `order_failed_topic` yerine `orderSubmitFailed-out-0` binding’i üzerinden telafi event’i gönderir.

### Akış Özeti

- `order-service` sipariş oluşturur ve `order_topic` içine event basar.
- `product-service`, `orderSubmitted` consumer’ı ile bu mesajı alır.
- İş kuralı uygun değilse `SubmitOrderFailedEvent` oluşturur.
- `StreamBridge`, `orderSubmitFailed-out-0` binding’i üzerinden compensating event’i Kafka’ya yollar.

## OpenFeign İsteklerinde Tracing Header Propagation

`TracingInterceptor`, `product-service` içinde tanımlanan bir `RequestInterceptor` bileşenidir. Amaç, OpenFeign ile gönderilen HTTP isteklerine aktif tracing context’i ekleyerek servisler arası iz zincirini korumaktır.

- `Tracer.currentSpan()` ile aktif span alınır
- Span mevcutsa request’e `b3` header’ı eklenir
- Header değeri `traceId-spanId-sampled` formatındadır
- Span yoksa interceptor sessizce pas geçer; request akışı bozulmaz

Bu yapı sayesinde `product-service` tarafından tetiklenen Feign çağrıları, `order-service` ile aynı trace içinde görünür. Böylece Zipkin veya benzeri tracing araçları kullanıldığında HTTP çağrıları ve işleme süreleri tek bir uçtan uca akış olarak izlenebilir.

## DLQ ve Hata Yönetimi Stratejisi

`product-service`, `orderSubmitted-in-0` consumer tarafında hata toleransı için DLQ (Dead Letter Queue) kullanır. Amaç, tekrar işlenemeyen veya business kuralı nedeniyle başarısız mesajları ana akıştan ayırmaktır.

### Mevcut DLQ ayarları

```yaml
spring:
  cloud:
    stream:
      kafka:
        bindings:
          orderSubmitted-in-0:
            consumer:
              enable-dlq: true
              dlq-name: order_dlq_topic
              auto-commit-on-error: false
              max-attempts: 3
```

- `enable-dlq: true`: başarısız tüketim sonunda mesajı DLQ'ya taşır
- `auto-commit-on-error: false`: hata alan offset'i başarı saymaz
- `max-attempts`: transient hatalarda sınırlı retry uygular

### DLQ ne zaman devreye girer?

- **Transient hata**: broker/network kısa süreli problemi; retry sonrası başarı mümkün
- **Poison message**: payload bozuk, schema uyumsuz, deserialize edilemeyen mesaj
- **Business validation failure**: domain kuralını ihlal eden mesaj (örn. stok/politika ihlali)

### DLQ operasyon önerileri

- `order_dlq_topic` için ayrı retention policy tanımlayın (örn. 3-7 gün)
- DLQ topic'ini doğrudan prod consumer'a bağlamayın; önce inceleme/reprocess adımı uygulayın
- Reprocess ederken aynı poison mesajın sonsuz döngüye girmesini engellemek için `x-retry-count` gibi header stratejisi kullanın
- DLQ metriklerini (`lag`, `message count`, `age`) alert kurallarıyla izleyin

### Reprocess Playbook (öneri)

1. DLQ mesajını sınıflandır: transient mi, kalıcı mı?
2. Kalıcı ise payload veya mapping düzeltmesi yap
3. Düzeltme sonrası mesajı kontrollü bir reprocess topic'ine taşı
4. Reprocess sonucunu audit log ile doğrula
5. Başarısızsa manuel incelemeye bırak

Bu yaklaşım, ana event akışını bloklamadan hatalı mesajları güvenli şekilde yönetmeyi sağlar.

## Konfigürasyon Dosyaları

### `application.yml`

Ortak geliştirme ayarları burada tutulur.

- **Uygulama adı:** `product-service`
- **Port:** `5002`
- **Boot Admin:** `http://localhost:8081`
- **Kafka broker:** `localhost:29092`
- **Eureka default zone:** `http://localhost:8761/eureka`
- **Kafka topic:** `order_topic`, `order_failed_topic`
- **DLQ topic:** `order_dlq_topic`
- **Actuator base path:** `/actuator`
- **Health probes:** aktif
- **Log dosya yolu:** `./logs/product-service/product-service.log`
- **Logstash hedefi:** `localhost:5044`

### `application-prod.yml`

Production ortamı için çevresel değişken odaklı ayarlar içerir.

- **Kafka broker kümesi:** `KAFKA_BROKERS`
- **Eureka default zone:** `EUREKA_DEFAULT_ZONE`
- **Boot Admin URL:** `SPRING_BOOT_ADMIN_URL`
- **Application port:** `SERVER_PORT`
- **Topic isimleri:** `ORDER_TOPIC`, `ORDER_FAILED_TOPIC`, `ORDER_DLQ_TOPIC`
- **Consumer retry davranışı:** `ORDER_MAX_ATTEMPTS`
- **Log dosya yolu:** `LOG_FILE_PATH` (varsayılan: `/var/log/product-service/product-service.log`)
- **Logstash hedefi:** `LOGSTASH_SERVER` (varsayılan: `logstash:5044`)
- **Log seviyesi:** `LOG_LEVEL_PRODUCT_SERVICE`
- **Tracing header propagation:** OpenFeign isteklerinde `b3` header’ı korunur

### Logback / ELK Template

Servis, `src/main/resources/logback_spring.xml` dosyasında hem dosya appender'ı hem de `LogstashTcpSocketAppender` kullanır.

```xml
<springProperty scope="context" name="logFile" source="logging.file.name"/>
<springProperty scope="context" name="logstashServer" source="logging.logstash.server"/>
<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${logFile:-./logs/product-service/product-service.log}</file>
</appender>
<appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
    <destination>${logstashServer:-localhost:5044}</destination>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder" />
</appender>
```

Bu template ile loglar hem dosyaya (`logging.file.name`) hem de Logstash'e (`logging.logstash.server`) gönderilir. ELK tarafında indeksleme için JSON encoder kullanımı korunur.

## Production Ortamı İçin Önemli Noktalar

### 1. Kafka ayarları

Üretimde local broker yerine çok düğümlü Kafka kümesi kullanılmalıdır.

- `auto-create-topics` kapatılmalıdır.
- `acks: all` ile veri güvenliği artırılmalıdır.
- `retries` değeri artırılmalıdır.
- Consumer tarafında DLQ aktif tutulmalıdır.

#### Neden `auto-create-topics` production'da kapatılmalı?

`auto-create-topics: true`, geliştirme ortamında hız kazandırır; ancak production'da aşağıdaki riskleri oluşturur:

- **Kontrolsüz topic oluşumu**: yazım hatasıyla yeni ve yanlış topic açılabilir (`order-topci` gibi)
- **Yanlış default ayarlar**: partition sayısı, replication factor, retention gibi kritik değerler broker default'u ile açılır
- **Operasyonel tutarsızlık**: ACL, naming convention, backup ve izleme politikaları devreye girmeden topic oluşur
- **SLA riski**: yanlış replication/partition ile performans ve dayanıklılık hedefleri bozulur

Öneri:

1. Production'da `auto-create-topics: false` kullanın
2. Topic'leri IaC (Terraform/Ansible) veya admin script ile önceden oluşturun
3. Her topic için `partitions`, `replication-factor`, `retention.ms`, `min.insync.replicas` değerlerini açıkça tanımlayın

### 2. Eureka ayarları

Prod ortamında servis kaydı ortam değişkeniyle yönetilmelidir.

- `EUREKA_DEFAULT_ZONE` ile doğru cluster adresi verilmelidir.
- `prefer-ip-address: true` sayesinde container içi erişim daha öngörülebilir olur.
- `lease-renewal` ve `lease-expiration` değerleri orchestration katmanına göre ayarlanmalıdır.

### 3. Health check ve hazır olma kontrolleri

Actuator health endpoint’leri production ortamda özellikle önemlidir.

- `liveness` probe: uygulama ayakta mı?
- `readiness` probe: trafik almaya hazır mı?
- `show-details: when_authorized`: sağlık bilgileri kontrollü görünmelidir.

### 4. Loglama

- Uygulama log seviyesi ortam bazlı yönetilmelidir.
- Kafka ve stream logları prod ortamda `WARN` seviyesine çekilebilir.
- Merkezi loglama varsa ELK / OpenSearch entegrasyonu önerilir.

#### ELK için örnek application ayarı

```yaml
logging:
  file:
    name: ./logs/product-service/product-service.log
  logstash:
    server: localhost:5044
  level:
    com.mertalptekin.productservice: INFO
```

Production örneği:

```yaml
logging:
  file:
    name: /var/log/product-service/product-service.log
  logstash:
    server: logstash:5044
```

### 5. Boot Admin entegrasyonu

Spring Boot Admin URL’si environment variable ile verilmelidir.

- Local: `http://localhost:8081`
- Container: servis adını kullanacak şekilde ayarlanmalıdır.

## Hangi Servislere Bağımlı?

### Zorunlu bağımlılıklar

- **Eureka Server**: Servis kaydı ve discovery
- **Kafka Broker**: Event tüketimi/üretimi
- **Spring Boot Admin**: Merkezi izleme

### İş akışına göre bağımlı servisler

- **Order Service**: `order_topic` event’lerinin kaynağıdır.
- **Gateway**: Dış dünyadan gelen isteklerin giriş noktası olabilir.

### Opsiyonel bağımlılıklar

- **Zipkin**: Bu servis şu anda Zipkin reporter bağımlılığı taşımıyor; ancak `TracingInterceptor` sayesinde trace context propagation hazırdır ve ileride eklenecek Zipkin entegrasyonu ile uçtan uca izleme desteklenebilir.
- **Config Server**: Şu anda kullanılmıyor.

## Docker / Ortam Hazırlığı

Aşağıdaki bileşenler çalışır durumda olmalıdır:

- `src/eureka-server`
- `src/admin-server`
- `docs/docker/docker/kafka/docker-compose.yml`

Opsiyonel bileşenler:

- `docs/docker/docker/elk/docker-compose.yml`
- `docs/docker/docker/zipkin/docker-compose.yml`

### Çalıştırma sırası

1. Kafka broker’ı başlatın.
2. Eureka Server’ı çalıştırın.
3. Spring Boot Admin Server’ı başlatın.
4. `order-service` ve `product-service` uygulamalarını çalıştırın.
5. Gerekirse Gateway üzerinden erişin.

## Lokal Çalıştırma

```bash
./mvnw spring-boot:run
```

Windows PowerShell için:

```powershell
.\mvnw.cmd spring-boot:run
```

## Üretim Notları

- `application.yml` içinde sabit değer bırakılmamalıdır.
- `application-prod.yml` üzerinden environment variable kullanılmalıdır.
- Kafka topic’leri önceden oluşturulmalıdır.
- DLQ topic’leri retention politikalarıyla birlikte yönetilmelidir.
- Health check endpoint’leri load balancer ve orchestration katmanı tarafından izlenmelidir.

## Notlar

- `application.properties` kaldırıldı ve YAML tabanlı yapı tercih edildi.
- Bu servis örnek senaryolarla hata ve gecikme davranışı göstermek için tasarlanmıştır.
- Event isimleri ile binding adları birebir uyumlu tutulmuştur.

