# Order Service

Bu modül, mikroservis mimarisinde **sipariş yönetimi** işlevlerini sağlayan bir mikroservis uygulamasıdır.
`order-service`, siparişlerin oluşturulması, yönetilmesi ve diğer servislerle etkilişimini koordine eder.

## Amaç

`order-service` projesinin temel hedefleri:

- Sipariş oluşturma ve yönetim API'leri sunmak
- Ürün servisiyle OpenFeign kullanarak servis-to-servis iletişim yapmak
- Kafka aracılığıyla event-driven mimariye katılmak (Saga pattern)
- Config Server'dan merkezi konfigürasyon almak
- Eureka üzerinden hizmet keşfi (service discovery) uygulamak
- Spring Boot Admin tarafından merkezi yönetilmek
- Zipkin ile dağıtık izleme (tracing) sağlamak

## Kullanılan Sürümler

`pom.xml` dosyasına göre:

- **Java:** 17
- **Spring Boot:** 3.5.9
- **Spring Cloud:** 2025.0.1
- **Build Tool:** Maven
- **Spring Boot Admin:** 3.5.6

## Kullanılan Teknolojiler

- **Spring Boot Web:** REST API geliştirme
- **Spring Cloud Config Client:** Merkezi konfigürasyon
- **Spring Cloud Eureka Client:** Servis keşfi
- **Spring Cloud OpenFeign:** Service-to-service HTTP iletişimi
- **Spring Cloud Stream + Kafka:** Event ve message handling
- **Spring Boot Admin Client:** Merkezi servis yönetimi
- **Micrometer Tracing + Zipkin:** Dağıtık iz sürme
- **Lombok:** Boilerplate kod azaltma
- **Spring Boot Test:** Test çalışması

## Bağımlılıklar

Ana bağımlılıklar (`pom.xml`):

- `org.springframework.cloud:spring-cloud-starter-config` - Config Server entegrasyonu
- `org.springframework.cloud:spring-cloud-starter-netflix-eureka-client` - Eureka client
- `org.springframework.cloud:spring-cloud-starter-openfeign` - Declarative HTTP client
- `org.springframework.cloud:spring-cloud-starter-loadbalancer` - Client-side load balancing
- `org.springframework.cloud:spring-cloud-starter-stream-kafka` - Kafka messaging
- `de.codecentric:spring-boot-admin-starter-client` - Spring Boot Admin integration
- `io.micrometer:micrometer-tracing` - Tracing framework
- `io.zipkin.reporter2:zipkin-reporter-brave` - Zipkin reporter
- `org.projectlombok:lombok` - Code generation

## Konfigürasyon

### Application YML

`src/main/resources/application.yml` içindeki temel ayarlar:

```yaml
spring:
  application:
    name: order-service
  profiles:
    active: dev
  cloud:
    config:
      uri: http://localhost:8085
    stream:
      kafka:
        binder:
          brokers: localhost:29092
      bindings:
        submitOrder-out-0:
          destination: order_topic
        orderSubmitFailed-in-0:
          destination: order_failed_topic
          group: order_failed_group
  boot:
    admin:
      client:
        enabled: true
        url: http://localhost:8081

server:
  port: 5001

management:
  endpoints:
    web:
      exposure:
        include: "*"
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

- **Uygulama adı:** `order-service`
- **Port:** `5001`
- **Profil:** `dev` (Config Server'dan `order-service-dev.yml` okunacak)
- **Config Server URI:** `http://localhost:8085`

### Kafka Stream Konfigürasyonu

Kafka ayarları `application.yml` içinde şu şekilde yapılandırılır:

```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers: localhost:29092
        bindings:
          submitOrder-out-0:
            producer:
              configuration:
                enable.idempotance: true
                acks: all
                retries: 3
      bindings:
        submitOrder-out-0:
          destination: order_topic
        orderSubmitFailed-in-0:
          destination: order_failed_topic
          group: order_failed_group
```

- **Kafka broker:** `localhost:29092`
- **Çıkış (Out) topic:** `order_topic` (sipariş oluşturma events)
- **Giriş (In) topic:** `order_failed_topic` (başarısız sipariş bildirimleri)
- **İdempotans:** Etkinleştirilmiş (`enable.idempotance: true` - duplicate event koruması)
- **ACKs:** `all` (tüm replikaların onayı)
- **Retries:** `3` (başarısız mesajlar 3 kez yeniden denenecek)

### Actuator ve Monitoring

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

- **Actuator:** Tüm endpoints açık (health, metrics, vb.)
- **Spring Boot Admin:** `http://localhost:8081` üzerinde merkezi yönetim
- **Zipkin:** %100 trace sampling (`1.0`)

## OpenFeign İle Servis-to-Service İletişimi

Order Service, **Product Service**'i çağırmak için OpenFeign kullanır:

```java
@FeignClient(name = "product-service")
public interface ProductClient {
    @GetMapping("/api/v1/products/{productId}")
    ProductResponse getProduct(@PathVariable String productId);
}
```

- Eureka üzerinden `product-service` instance'ı dinamik olarak bulunur
- Load balancing otomatik olarak uygulanır
- Circuit breaker pattern'ı uygulanabilir

### OpenFeign Nasıl Çalışır?

1. **Service Discovery:** OpenFeign, Eureka'dan `product-service` adlı servisi bulur
2. **Load Balancing:** Spring Cloud LoadBalancer, mevcut instance'lar arasında yük dağıtır
3. **Circuit Breaker:** Kütüphanede yapılandırılırsa, başarısız istekleri tollayabilir
4. **Retry Mekanizması:** Ağ hatalarında otomatik retry yapılabilir

### OpenFeign İsteklerinde Tracing Header Propagation

`TracingInterceptor`, Feign istekleri gönderilmeden hemen önce devreye giren bir `RequestInterceptor` bileşenidir. Amaç, mevcut tracing context'i downstream servise taşımaktır.

- `Tracer.currentSpan()` ile o anda aktif olan span alınır
- Span varsa `b3` header'ı request'e eklenir
- Header içeriği `traceId-spanId-sampled` formatındadır
- Böylece `order-service` → `product-service` çağrıları aynı trace zinciri içinde izlenebilir

Bu yaklaşım, distributed tracing akışında context propagation sağlar ve Zipkin üzerinde servisler arası çağrıların tek bir iz olarak görünmesine yardımcı olur.

### Production'da OpenFeign Konfigürasyonu

```yaml
feign:
  client:
    config:
      product-service:
        connectTimeout: 5000        # 5 saniye bağlantı timeout
        readTimeout: 10000          # 10 saniye okuma timeout
        loggerLevel: FULL           # Tüm request/response loglanır
        retryer: com.github.openfeign.Retryer$Default

resilience4j:
  circuitbreaker:
    instances:
      product-service:
        registerHealthIndicator: true
        slidingWindowSize: 10           # Son 10 istemi analiz et
        minimumNumberOfCalls: 5         # En az 5 isteğin başarısız olması gerekir
        failureRateThreshold: 50        # %50 fail rate'de circuit aç
        slowCallRateThreshold: 80       # %80 slow call oranında warning
        slowCallDurationThreshold: 2000 # 2 saniyeden uzun = slow call
        permittedNumberOfCallsInHalfOpenState: 3
        waitDurationInOpenState: 60s
        recordExceptions:
          - java.util.concurrent.TimeoutException
          - java.io.IOException
          - feign.FeignException
```

### OpenFeign Hata Ele Alması

```java
@FeignClient(name = "product-service", fallback = ProductClientFallback.class)
public interface ProductClient {
    @GetMapping("/api/v1/products/{productId}")
    ProductResponse getProduct(@PathVariable String productId);
}

@Component
public class ProductClientFallback implements ProductClient {
    @Override
    public ProductResponse getProduct(String productId) {
        // Fallback: cached veya default değer döndür
        return new ProductResponse(productId, "Ürün bilgisi şu an kullanılamıyor", 0);
    }
}
```

## Spring Cloud Stream + Kafka Entegrasyonu

Order Service, asenkron event-driven iletişim için Kafka'yı kullanır. Spring Cloud Stream, Kafka producer/consumer abstraktını sağlar.

### Kafka Event Flow

Order Service'in Saga pattern implementasyonu:

```
1. İstemci sipariş oluştur → Order Service
2. Order Service → order_topic (submitOrder event yayınla)
3. Product Service dinle → order_topic
4. Product Service → Stok kontrolü yap
5. Başarılı → order_topic'e reply
6. Başarısız → order_failed_topic'e event gönder
7. Order Service dinle → order_failed_topic
8. Order Service → Siparişi iptal et
```

### Spring Cloud Stream Kafka Konfigürasyonu

```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers: localhost:29092
          # Kafka broker cluster adresleri
          auto-create-topics: true
          # Yazılan topic'ler otomatik oluşturulsun
        bindings:
          # Producer konfigürasyonu
          submitOrder-out-0:
            producer:
              configuration:
                # İdempotent producer: duplicate message yok
                enable.idempotance: true
                # Tüm replikalar onaylayana kadar bekle
                acks: all
                # Başarısız message'ı 3 kez retry et
                retries: 3
                # Snappy sıkıştırma kullan (CPU vs Network trade-off)
                compression.type: snappy
                # Batch timeout: mesaj göndericisi 100ms bekler
                linger.ms: 100
                # Batch size: 16KB toplayan sonra gönder
                batch.size: 16384
      bindings:
        # Topic haritalaması
        submitOrder-out-0:
          destination: order_topic
          # Partitioning: sipariş ID'sine göre partition seç
          producer:
            partitionKeyExpression: headers['orderId']
        
        # Consumer konfigürasyonu
        orderSubmitFailed-in-0:
          destination: order_failed_topic
          # Consumer group: birden fazla instance'ın mesajları paylaşması
          group: order_failed_group
          # Her partition'ı sadece bir consumer handle etsin
          consumer:
            concurrency: 3
            maxAttempts: 3
            backOffInitialInterval: 1000
```

### Java'da Kafka Producer (Event Yayınlama)

```java
@Service
public class OrderEventPublisher {
    private final StreamBridge streamBridge;
    
    public OrderEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }
    
    public void publishOrderCreated(OrderEvent event) {
        // submitOrder-out-0 binding'ine mesaj gönder
        streamBridge.send("submitOrder-out-0", 
            MessageBuilder
                .withPayload(event)
                .setHeader("orderId", event.getOrderId())
                .build()
        );
    }
}
```

### Java'da Kafka Consumer (Event Dinleme)

```java
@Component
public class OrderFailedEventListener {
    
    @Bean
    public Consumer<Message<OrderFailedEvent>> orderSubmitFailed() {
        return message -> {
            OrderFailedEvent event = message.getPayload();
            // Siparişi iptal et
            handleOrderFailure(event.getOrderId(), event.getError());
        };
    }
}
```

### Kafka Partition Stratejisi

Order Service için sipariş ID'sine göre partitioning yapıldığında:
- Aynı müşterinin siparişleri aynı partition'a gider
- Consumer group'ta tek consumer bu partition'ı işler
- **Order guarantee:** Aynı müşterin siparişleri sıralı işlenir (FIFO)
- **Scalability:** Müşteri sayısı artsça yeni partition'lar eklenebilir

## Zipkin ile Dağıtık İz Sürme (Distributed Tracing)

Zipkin, microservis'ler arası istek akışını görselleştirerek performans problemlerini teşhis etmeye yardımcı olur.

### Zipkin Entegrasyonu Nasıl Çalışır?

1. **Trace Başlatma:** İstemci → Gateway request'i gelince unique trace ID oluşturulur
2. **Span Oluşturma:** Herbir servis granüler işlemi (span) kaydeder
   - Span başlangıç zamanı
   - Span bitiş zamanı
   - Servis adı
   - Operation adı
   - Error flag
3. **Context Propagation:** Trace ID ve bazı span bilgileri HTTP header'larına yazılır
4. **Reporter:** Micrometer-Brave, span'ları Zipkin'e gönderir

### Application'da Zipkin Konfigürasyonu

```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # Production'da 0.01 - 0.1 olmalı
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
      # Zipkin URL
      timeout: 10s
      # Zipkin'e max 10 saniye bekleme
      connectTimeout: 5s
```

### Sampling Stratejieleri

**Development (Test):**
```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # %100: tüm trace'leri topla
```
- Her request için trace oluşturulur
- Debugging için yardımcı
- Overhead yüksek

**Production (Critical Path):**
```yaml
management:
  tracing:
    sampling:
      probability: 0.05  # %5: her 20 request'in 1'ini trace et
```
- CPU/Network overhead düşük
- Anormallikleri tespit etmek için yeterli
- Tamamen akışkanlık ararsan 0.1 kullan

**Custom Sampling (Bağlama göre):**

```yaml
management:
  tracing:
    sampling:
      probability: 0.01  # Base: %1

spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          predicates:
            - Path=/order-service/**
          filters:
            - name: TraceId
              args:
                samplingProbability: 0.1  # Order-service traceleri %10 sample et
```

### Zipkin'de Trace Görüntüleme

Zipkin UI: `http://localhost:9411`

1. **Service:** `order-service` seç
2. **Span Name:** `POST /api/v1/orders` seç
3. **Min Duration:** 100ms (slow query)
4. **Search** butonuna bas

Çıktıda görülen bilgiler:
- **Trace ID:** Istek'in benzersiz kimliği
- **Spans:** Her servisin işlem detayları
- **Timeline:** Hangi serviste ne kadar zaman geçti
- **Dependencies:** Hizmetler arası iletişim grafiği

### Özel Span Oluşturma (Java'da)

```java
@Service
public class OrderService {
    private final Tracer tracer;
    
    public OrderService(Tracer tracer) {
        this.tracer = tracer;
    }
    
    public Order createOrder(OrderRequest request) {
        Span customSpan = tracer.nextSpan()
            .name("createOrder")
            .tag("orderId", request.getOrderId())
            .tag("customerId", request.getCustomerId())
            .start();
        
        try (Tracer.SpanInScope ws = tracer.withSpan(customSpan)) {
            // Siparişi oluştur
            Order order = new Order(request);
            customSpan.tag("status", "success");
            return order;
        } catch (Exception e) {
            customSpan.tag("error", "true");
            customSpan.error(e);
            throw e;
        } finally {
            customSpan.finish();
        }
    }
}
```

## Production İçin Önemli Ayarlar

### 1) High Availability (HA) Konfigürasyonu

Bu bölümdeki production ayarları uygulamanın çoklu instance çalışmasına uygun hale getirilmesini hedefler:

- `server.port: 5001`
- `server.shutdown: graceful`
- `server.tomcat.threads.max: 200`
- `server.tomcat.threads.min-spare: 10`
- `server.tomcat.accept-count: 100`
- `server.tomcat.connection-timeout: 20000ms`
- `server.servlet.session.timeout: 30m`
- `spring.application.name: order-service`
- `spring.application.version: 1.0.0`
- `eureka.instance.prefer-ip-address: false`
- `eureka.instance.lease-renewal-interval-in-seconds: 30`
- `eureka.instance.lease-expiration-duration-in-seconds: 90`
- `eureka.instance.health-check-url-path: /actuator/health`
- `eureka.instance.status-page-url-path: /actuator/info`
- `eureka.client.service-url.defaultZone`: `https://eureka1.production.com:8761/eureka/,https://eureka2.production.com:8761/eureka/`
- `eureka.client.register-with-eureka: true`
- `eureka.client.fetchRegistry: true`

### 2) Database Connection Pooling (Hikari)

```yaml
spring:
  datasource:
    hikari:
      # Connection pool size
      maximum-pool-size: 20
      minimum-idle: 5
      # Idle connection timeout
      idle-timeout: 600000
      # Max lifetime
      max-lifetime: 1800000
      # Bağlantı testi
      connection-test-query: "SELECT 1"
      # Auto-commit
      auto-commit: true
```

### 3) Circuit Breaker Tuning

```yaml
resilience4j:
  circuitbreaker:
    instances:
      product-service:
        # Circuiti açma şartı
        registerHealthIndicator: true
        slidingWindowSize: 20          # Son 20 isteği analiz et
        minimumNumberOfCalls: 10       # En az 10 isteğin sonucu gerekli
        failureRateThreshold: 50       # %50 hata oranında aç
        slowCallRateThreshold: 80      # %80 slow call'da uyar
        slowCallDurationThreshold: 3000 # 3 saniye = slow
        
        # Circuit açıkken davranış
        permittedNumberOfCallsInHalfOpenState: 5
        waitDurationInOpenState: 120s
        
        # Retry mekanizması
        recordExceptions:
          - java.util.concurrent.TimeoutException
          - feign.FeignException$ServiceUnavailable
          - java.net.ConnectException
        ignoreExceptions:
          - com.example.BusinessException
  
  retry:
    instances:
      product-service:
        maxAttempts: 3
        waitDuration: 1000
        retryExceptions:
          - java.util.concurrent.TimeoutException

  timelimiter:
    instances:
      product-service:
        timeoutDuration: 5s
        cancelRunningFuture: true
```

### 4) Kafka Production Settings

```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          # Broker cluster
          brokers: kafka-1:29092,kafka-2:29092,kafka-3:29092
          # Replication factor
          replication-factor: 3
          # Min in-sync replicas
          min-in-sync-replicas: 2
        bindings:
          submitOrder-out-0:
            producer:
              configuration:
                # High durability
                acks: all
                # Retries
                retries: 5
                # Backoff
                retry.backoff.ms: 100
                # Idempotency
                enable.idempotance: true
                # Compression
                compression.type: snappy
                # Batching
                linger.ms: 100
                batch.size: 32768
                # Transaction support (Exactly-once semantics)
                transactional.id: order-service-${HOSTNAME}
                
          orderSubmitFailed-in-0:
            consumer:
              configuration:
                # Consumer behavior
                isolation.level: read_committed
                # Auto offset reset
                auto.offset.reset: earliest
                # Group ID
                group.id: order-failed-group
                # Session timeout
                session.timeout.ms: 30000
              # Concurrency
              concurrency: 5
              # Error handling
              maxAttempts: 3
              backOffInitialInterval: 1000
              backOffMaxInterval: 10000
```

### 5) Logging Configuration

```yaml
logging:
  level:
    root: WARN
    com.mertalptekin.orderservice: INFO
    org.springframework.cloud.gateway: INFO
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: /var/log/order-service/order-service.log
    max-size: 10MB
    max-history: 30
    total-size-cap: 1GB
```

### 6) Actuator ve Health Checks

```yaml
management:
  endpoints:
    web:
      base-path: /actuator
      exposure:
        include: health,info,prometheus,metrics,circuitbreakers
      exclude: env,configprops
      path-mapping:
        health: health-check
  endpoint:
    health:
      show-details: when-authorized
      show-components: when-authorized
      probes:
        enabled: true
  health:
    circuitbreakers:
      enabled: true
    livenessState:
      enabled: true
    readinessState:
      enabled: true
  metrics:
    enable:
      jvm: true
      process: true
      system: true
    export:
      prometheus:
        enabled: true
  tracing:
    sampling:
      probability: 0.05  # %5 sampling
```

### 7) Resource Limits

```yaml
spring:
  mvc:
    async:
      request-timeout: 30000  # 30 saniye
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

server:
  tomcat:
    max-http-post-size: 10485760
```

## Production Konfigürasyonu (`application-prod.yml`)

Production ortamında order-service'i başlatmak için şu command kullanılır:

```shell
java -Dspring.profiles.active=prod -jar order-service.jar
```

### Production YML Yapılandırması (application-prod.yml)

Production ortamında yapılan temel konfigürasyonlar:

#### 1) Kafka Broker Cluster (High Availability)

```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          # Production'da 3+ Kafka broker gerekli
          brokers: kafka-1:9092,kafka-2:9092,kafka-3:9092
          # Replication factor: her topic'in 3 kopyası
          replication-factor: 3
          # Min in-sync replicas: yazma işlemi tamamlandıktan sonra
          min-in-sync-replicas: 2
```

**Açıklama:**
- **Replication Factor = 3:** Kafka cluster'daki her broker'da topic'in bir kopyası var
- **Min In-Sync Replicas = 2:** Message yazılmadan önce en az 2 replica onay vermelidir
- **Örnek:** 1 broker down olsa bile, diğer 2 broker verileriveri işlemeye devam eder

#### 2) Producer Configuration (Message Gönderimi)

```yaml
spring:
  cloud:
    stream:
      kafka:
        bindings:
          submitOrder-out-0:
            producer:
              configuration:
                # Exactly-once semantics
                enable.idempotance: true          # Duplicate message yok
                acks: all                         # Tüm replika onayı gerekli
                retries: 5                        # Başarısız 5 kez retry
                retry.backoff.ms: 100             # Retry delay
                compression.type: snappy          # Network optimization
                linger.ms: 100                    # Batch window
                batch.size: 32768                 # 32KB batch
                # Transaction support
                transactional.id: order-service-${HOSTNAME}
```

**Açıklama:**
- **`acks: all`:** Producer, tüm in-sync replikalara yazıldıktan sonra başarı döndürür
- **Batch Window:** 100ms'de batch toplanır, ya da 32KB dolunca gönderilir
- **Transactional.id:** Aynı producer'ın duplikat message gönderip göndermediğini takip eder (exactly-once)

#### 3) Consumer Configuration (Message Dinleme)

```yaml
      bindings:
        orderSubmitFailed-in-0:
          consumer:
            concurrency: 5                    # 5 parallel consumer thread
            max-attempts: 3                   # Failure'da 3 retry
            back-off-initial-interval: 1000   # İlk delay: 1 saniye
            back-off-max-interval: 10000      # Max delay: 10 saniye
            configuration:
              isolation.level: read_committed # Committed message'ları oku (exactly-once)
              session.timeout.ms: 30000       # Session 30 saniye timeout
```

**Açıklama:**
- **Concurrency=5:** 5 consumer paralel olarakNa test order_failed_topic'i dinler
- **Isolation Level:** `read_committed` → uncommitted message'lar skip edilir
- **Backoff Strategy:** İlk retry'da 1s, sonraki retry'lar exponential olarak artır

#### 4) Eureka Client Configuration (Service Discovery)

```yaml
eureka:
  instance:
    prefer-ip-address: false                # Hostname kullan (IP yerine)
    lease-renewal-interval-in-seconds: 30   # Eureka'ya heartbeat interval
    lease-expiration-duration-in-seconds: 90 # Instance'ı dead saymadan önceki timeout
    
  client:
    service-url:
      defaultZone: https://eureka-1.production.com:8761/eureka/,
                   https://eureka-2.production.com:8761/eureka/,
                   https://eureka-3.production.com:8761/eureka/
```

**Açıklama:**
- **Multiple Eureka Servers:** HA (High Availability) için 3+ Eureka server
- **Hostname Kullanımı:** DNS resolution'ın daha reliable olması
- **Heartbeat Interval:** Her 30 saniyede Eureka'ya canlı olduğu sinyali gönder

#### 5) Circuit Breaker Configuration (Fault Tolerance)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      product-service:
        sliding-window-size: 20              # Son 20 istek analiz et
        minimum-number-of-calls: 10          # Atleast 10 call gerekli
        failure-rate-threshold: 50           # %50 failure → circuit aç
        slow-call-duration-threshold: 3000   # 3 saniye > slow call
        permitted-number-of-calls-in-half-open-state: 5  # Half-open'da 5 call test et
        wait-duration-in-open-state: 120s    # Open state 2 dakika bekle
```

**Açıklama:**
- **3 Circuit Breaker State:**
  1. **CLOSED (Normal):** Request'ler product-service'e gidiyor
  2. **OPEN (Fail):** %50+ failure rate → Tüm request'ler fail dönüyor (fast-fail)
  3. **HALF_OPEN (Recovery):** 120 saniye sonra 5 request test et, başarılı ise CLOSED'a dön

#### 6) OpenFeign Timeout Configuration

```yaml
feign:
  client:
    config:
      product-service:
        connect-timeout: 5000    # 5 saniye bağlantı timeout
        read-timeout: 10000      # 10 saniye okuma timeout
        logger-level: FULL       # Debug logs açık
```

**Açıklama:**
- Product Service yanıt vermezse 10 saniye sonra connection timeout oluşturur
- Circuit breaker'a hata bilgisi gönderilir

#### 7) Zipkin Tracing Configuration

```yaml
management:
  tracing:
    sampling:
      probability: 0.05          # %5 sampling rate
  zipkin:
    tracing:
      endpoint: https://zipkin.production.com/api/v2/spans
      timeout: 10s               # Zipkin'e max 10s bekleme
```

**Açıklama:**
- **%5 Sampling:** Production'da CPU overhead'i azaltmak için her 20 request'in 1'ini trace et
- **Zipkin Endpoint:** Merkezi tracing server'ı monitorledi

#### 8) Health Check & Actuator Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics,circuitbreakers
        exclude: env,configprops
  
  health:
    circuitbreakers:
      enabled: true
    kafka:
      enabled: true
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

**Açıklama:**
- **Kubernetes Probes:**
  - **Liveness:** Container'ı yeniden başlatan health check
  - **Readiness:** Traffic göndermeyen health check
- **Exposed Endpoints:** Prometheus metrics ve circuit breaker status görüyorsünüz

#### 9) Logging Configuration

```yaml
logging:
  level:
    root: WARN
    com.mertalptekin.orderservice: INFO
    org.springframework.cloud: INFO
  
  file:
    name: /var/log/order-service/order-service.log
    max-size: 100MB
    max-history: 30
    total-size-cap: 1GB
```

**Açıklama:**
- **FILE ROTATION:** Her 100MB'da yeni log file oluşturur
- **RETENTION:** Son 30 gün log tutulur
- **TOTAL CAP:** Toplam 1GB'dan fazla log dosyası tutulmaz

#### 10) Database Connection Pooling (HikariCP)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20        # Max bağlantı pool size
      minimum-idle: 5              # Min idle bağlantı
      idle-timeout: 600000         # 10 dakika sonra idle connection'ları kapat
      max-lifetime: 1800000        # Max 30 dakika bağlantı ömrü
      connection-test-query: "SELECT 1"
```

**Açıklama:**
- **Connection Pool:** Database'e çok fazla bağlantı maliyet oluşturmaması içinıştır
- **20 Connection:** Orta denetleme bir microservis için yeterli
- **Connection Reuse:** Pool'dan bağlantı tekrar kullanılır (her seferinde yeni bağlantı değil)

---

## Order Service Bağımlılıkları (Dependencies)

Order Service aşağıdaki microservislere ve harici bileşenlere **bağımlıdır:**

### 1) **Product Service** ⚠️ **(Kritik Bağımlılık)**

**Amaç:** Sipariş edilen ürünlerin detaylarını çekmek

**OpenFeign Client:**
```java
@FeignClient(name = "product-service")
public interface ProductClient {
    @PostMapping("/api/v1/products/details")
    ResponseEntity<OrderedProductDetailResponse> getOrderedProducts(
        @RequestBody OrderedProductDetailRequest request
    );
}
```

**Service Discovery:** Eureka üzerinden dinamik bulunur

**Network Protocol:** HTTP/REST

**Fallback Mekanizması:** Circuit breaker ile otomatik fallback

**Bağlantı Ayarları (Production):**
```yaml
feign:
  client:
    config:
      product-service:
        connectTimeout: 5000
        readTimeout: 10000
```

---

### 2) **Config Server** ⚠️ **(Kritik Bağımlılık)**

**Amaç:** Uygulamadaki tüm configuration'ları merkezi olarak yönetmek

**Default URL:** `http://localhost:8085`

**Production URL:** `https://config-server.production.com:8085`

**Konfigürasyon Dosyaları:**
- `order-service.yml` - Global config
- `order-service-dev.yml` - Development profili
- `order-service-prod.yml` - Production profili
- `order-service-staging.yml` - Staging profili

**Başarısız Olursa:**
```yaml
spring:
  cloud:
    config:
      failFast: true  # Config Server'dan config alınamazsa app başlamaz
```

---

### 3) **Eureka Server** ⚠️ **(Kritik Bağımlılık)**

**Amaç:** Service discovery ve load balancing

**Default URL:** `http://localhost:8761/eureka/`

**Production URLs:**
```yaml
eureka:
  client:
    service-url:
      defaultZone: https://eureka-1.production.com:8761/eureka/,
                   https://eureka-2.production.com:8761/eureka/,
                   https://eureka-3.production.com:8761/eureka/
```

**Funksiyonları:**
1. Order service'i Eureka'ya register et
2. Product service instance'larını discovery et
3. Service availability monitoring

---

### 4) **Apache Kafka** ⚠️ **(Kritik Bağımlılık)**

**Amaç:** Event-driven architecture ve Saga pattern'ını uygulamak

**Kafka Brokers (Production):**
```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers: kafka-1:9092,kafka-2:9092,kafka-3:9092
```

**Topics:**
| Topic | Türü | Amaç | Consumer |
|-------|------|------|----------|
| `order_topic` | OUT (Producer) | Yeni sipariş event'leri | Product Service |
| `order_failed_topic` | IN (Consumer) | Sipariş iptal event'leri | Order Service |

**Event Flow (Saga Pattern):**
```
Order Service                Kafka                  Product Service
    │
    ├─ POST /orders ────┐
    │                   │
    │                   ├─> [order_topic]
    │                   │
    │                   ├──────────────────> Consume (product stock check)
    │                   │
    │                   │ (If stock insufficient)
    │                   ├─> [order_failed_topic]
    │                   │
    │ <──────────────────┤
    │                   ^
    └─ Rollback order ──┘
```

---

### 5) **Spring Boot Admin Server** (Optional)

**Amaç:** Merkezi monitoring ve management

**Default URL:** `http://localhost:8081`

**Production URL:** `https://admin-server.production.com:8081`

**Sağlanan İşlevler:**
- Application health monitoring
- Environment & properties viewing
- Log level management
- Endpoint invocation
- JVM metrics

---

### 6) **Zipkin Server** (Optional)

**Amaç:** Distributed tracing ve performance monitoring

**Default URL:** `http://localhost:9411`

**Production URL:** `https://zipkin.production.com`

**Trace Information:**
- Request flow across microservices
- Latency breakdown per service
- Error tracking and debugging

---

### 7) **Keycloak** (Optional - Future Integration)

**Amaç:** OAuth 2.0 / OIDC kimlik doğrulama

**Default URL:** `http://localhost:8180`

**Production URL:** `https://keycloak.production.com`

**Not:** Şu anda passive olarak var, Spring Security entegrasyonu yapıldığında aktif olur

---

## Bağımlılık Özeti Tablosu

| Bağımlılık | Gerekli | Başarısız Olursa | Network | Port |
|------------|---------|------------------|---------|------|
| Product Service | ✅ Evet | Circuit breaker activate | HTTP/REST | 5002 |
| Config Server | ✅ Evet | App başlamaz | HTTPS | 8085 |
| Eureka Server | ✅ Evet | Service discovery fail | HTTPS | 8761 |
| Kafka Broker | ✅ Evet | Event processing fail | TCP | 9092 |
| Spring Boot Admin | ❌ Hayır | Monitoring unavailable | HTTPS | 8081 |
| Zipkin | ❌ Hayır | Tracing unavailable | HTTPS | 9411 |
| Keycloak | ❌ Hayır | Auth disabled | HTTPS | 8180 |

---

## Docker Compose Dosyaları

Order Service uygulaması çalıştırmak için gerekli tüm bileşenler Docker container'larında çalışılır.

### Proje Yapısı

```
docs/docker/docker/
├── kafka/
│   ├── docker-compose.yml (Kafka + Zookeeper + Debezium + PostgreSQL)
├── zipkin/
│   ├── docker-compose.yml (Zipkin distributed tracing)
├── keycloak/
│   ├── docker-compose.yml (Keycloak auth server)
├── redis/
│   ├── docker-compose.yml (Redis cache)
└── elk/
    ├── docker-compose.yml (Elasticsearch + Logstash + Kibana)
```

### 1) Kafka Docker Compose

**Dosya:** `docs/docker/docker/kafka/docker-compose.yml`

**Bileşenler:**
- **Zookeeper (Port 2181):** Kafka cluster coordination
- **Kafka Broker (Port 29092):** Message broker
- **Debezium Connect (Port 8083):** CDC (Change Data Capture) for PostgreSQL
- **PostgreSQL (Port 5432):** Debezium için source database
- **Kafka UI (Port 8088):** Kafka monitoring dashboard

**Başlat:**
```bash
cd docs/docker/docker/kafka
docker-compose up -d
```

**Doğrulama:**
```bash
# Kafka UI'a bağlan
http://localhost:8088

# Topics kontrol et
docker exec kafka kafka-topics --list --bootstrap-server kafka:9092
```

---

### 2) Zipkin Docker Compose

**Dosya:** `docs/docker/docker/zipkin/docker-compose.yml`

**Bileşenler:**
- **Zipkin Server (Port 9411):** Distributed tracing UI ve collector

**Başlat:**
```bash
cd docs/docker/docker/zipkin
docker-compose up -d
```

**Doğrulama:**
```bash
# Zipkin UI'a bağlan
http://localhost:9411
```

---

### 3) Keycloak Docker Compose

**Dosya:** `docs/docker/docker/keycloak/docker-compose.yml`

**Bileşenler:**
- **Keycloak (Port 8180):** OAuth 2.0 / OIDC auth server

**Başlat:**
```bash
cd docs/docker/docker/keycloak
docker-compose up -d
```

**Doğrulama:**
```bash
# Keycloak admin console
http://localhost:8180
# Username: admin
# Password: admin
```

---

### 4) Redis Docker Compose

**Dosya:** `docs/docker/docker/redis/docker-compose.yml`

**Bileşenler:**
- **Redis Server (Port 6379):** In-memory data store (optional caching)
- **Redis Insight (Port 5540):** Redis GUI management

**Başlat:**
```bash
cd docs/docker/docker/redis
docker-compose up -d
```

**Doğrulama:**
```bash
# Redis Insight GUI
http://localhost:5540

# Redis CLI
docker exec redis redis-cli -a Neominal
```

---

### 5) ELK Stack Docker Compose

**Dosya:** `docs/docker/docker/elk/docker-compose.yml`

**Bileşenler:**
- **Elasticsearch (Port 9200):** Log storage ve indexing
- **Logstash (Port 5044):** Log processing ve transformation
- **Kibana (Port 5601):** Log visualization dashboard

**Başlat:**
```bash
cd docs/docker/docker/elk
docker-compose up -d
```

**Doğrulama:**
```bash
# Kibana UI
http://localhost:5601

# Elasticsearch health check
curl http://localhost:9200/_cluster/health
```

---

## Tam Uygulama Başlatma Adımları

### 1. Development Environment Setup

```bash
# Adım 1: Kafka + broker'ları başlat
cd docs/docker/docker/kafka
docker-compose up -d

# Adım 2: Zipkin başlat
cd ../zipkin
docker-compose up -d

# Adım 3: Config Server'ı başlat (Spring Boot App)
cd ../../src/config-server
mvn spring-boot:run

# Adım 4: Eureka Server'ı başlat (Spring Boot App)
cd ../eureka-server
mvn spring-boot:run

# Adım 5: Product Service'i başlat
cd ../product-service
mvn spring-boot:run

# Adım 6: Order Service'i başlat (dev profile)
cd ../order-service
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"

# Adım 7: Spring Boot Admin başlat (optional)
cd ../admin-server
mvn spring-boot:run
```

### 2. Production Environment Deployment

**Docker Image Build:**
```bash
cd src/order-service
docker build -t order-service:1.0.0 .
```

**Kubernetes YAML:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      containers:
      - name: order-service
        image: order-service:1.0.0
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: prod
        - name: JAVA_OPTS
          value: "-Xmx512m -Xms256m"
        ports:
        - containerPort: 5001
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 5001
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 5001
          initialDelaySeconds: 15
          periodSeconds: 5
```

---

## Notlar

- `application.properties` kaldırılmıştır; proje `application.yml` ile çalışacak şekilde düzenlendi
- Kafka idempotency ayarları event-driven Saga pattern'ında çok önemlidir
- OpenFeign ve Eureka, order service'in diğer servislerle bağlantısını dinamik hale getirir
- Config Server'dan merge edilen properties mevcut YAML konfigürasyonunu override edebilir
- Production'da Zipkin sampling'i mutlaka optimize et (CPU/Network maliyet)
- Circuit breaker ayarları hedef servise göre fine-tune edilmeli
- **Production Profili:** `spring.profiles.active=prod` ile uygulamayı başlat
- **High Availability:** Production'da minimum 3 replica order-service instance çalıştır
- **Database:** Production'da PostgreSQL kullan (embedded H2 değil)
- **Logs:** ELK Stack'i kullanarak merkezi log yönetimi sağla
