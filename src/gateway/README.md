# Gateway

Bu modül, mikroservis mimarisinde istemci isteklerini tek noktadan karşılayan **API Gateway** uygulamasıdır.
`gateway`, istekleri ilgili servislere yönlendirir, güvenlik kontrolü uygular, rate limit/circuit breaker gibi dayanıklılık kuralları çalıştırır.

## Amaç

`gateway` projesinin temel hedefleri:

- Dış istemci trafiğini tek giriş noktasında toplamak
- Route bazlı yönlendirme yapmak (`order-service`, `product-service`)
- JWT tabanlı erişim kontrolü uygulamak
- Rate limiter ve circuit breaker ile hata etkisini azaltmak
- İzlenebilirlik için actuator ve tracing verisi üretmek

## Kullanılan Sürümler

`pom.xml` dosyasına göre:

- **Java:** 17
- **Spring Boot:** 3.5.9
- **Spring Cloud:** 2025.0.1
- **Build Tool:** Maven

## Gateway Projesinde Kullanılan Paketler ve Teknolojiler

Ana bağımlılıklar (`pom.xml`):

- `org.springframework.cloud:spring-cloud-starter-gateway-server-webflux`
- `org.springframework.cloud:spring-cloud-starter-netflix-eureka-client`
- `org.springframework.boot:spring-boot-starter-security`
- `org.springframework.boot:spring-boot-starter-oauth2-resource-server`
- `org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j`
- `org.springframework.boot:spring-boot-starter-data-redis-reactive`
- `org.springframework.boot:spring-boot-starter-actuator`
- `io.micrometer:micrometer-tracing`
- `io.micrometer:micrometer-tracing-bridge-brave`
- `io.zipkin.reporter2:zipkin-reporter-brave`
- `io.zipkin.reporter2:zipkin-sender-okhttp3`
- `io.zipkin.reporter2:zipkin-sender-kafka`

Kod seviyesinde kullanılan başlıca paketler:

- `org.springframework.cloud.gateway.*` (routing, filter, rate limit)
- `org.springframework.security.*` (WebFlux security, JWT)
- `reactor.core.publisher.Mono` (reaktif akış)
- `io.github.resilience4j.*` (circuit breaker/timelimiter - konfigürasyon üzerinden)

## Konfigürasyon ve Route Yapısı

`src/main/resources/application.yml` içindeki route tanımları:

- `Path=/order-service/api/v1/**` -> `lb://order-service`
- `Path=/product-service/api/v1/**` -> `lb://product-service`

Ek olarak:

- Redis tabanlı rate limiter (`RequestRateLimiter`)
- Resilience4j circuit breaker (`productServiceBreaker`)
- Keycloak tabanlı JWT doğrulama (`issuer-uri`, `jwk-set-uri`)
- Actuator ve Zipkin tracing ayarları

## Keycloak Entegrasyonu ve OAuth2.0 / OpenID Connect

### Genel Mimari

`gateway` projesi, **OAuth2.0 Resource Server** olarak çalışır. İstemciler bir JWT access token ile istekte bulunurlar ve gateway bu token'ı Keycloak'tan çekilen public key'lerle doğrular.

#### Akış:
1. İstemci → Keycloak'a login (credentials/OIDC)
2. Keycloak → JWT token üretir
3. İstemci → Gateway'e JWT ile istek gönderir
4. Gateway → Token'ı Keycloak public key'lerle doğrular
5. Token geçerse → Hedef servise yönlendir; geçmezse → 401 Unauthorized

### Application.yml İçinde Keycloak Ayarları

`src/main/resources/application.yml` içindeki OAuth2 bölümü:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8189/realms/AuthServer
          jwk-set-uri: http://localhost:8189/realms/AuthServer/protocol/openid-connect/certs
```

- **`issuer-uri`:** Keycloak realm'ının identity provider URL'si. Token'ın kimin tarafından üretildiğinin doğrulanması için kullanılır.
- **`jwk-set-uri`:** Keycloak'ın public key'lerini (JWKS) sağladığı endpoint. Gateway bu endpoint'ten anahtarları çeker ve token imzasını doğrular.

### Keycloak Tarafında Yapılması Gereken Konfigürasyon

#### 1) Realm Oluşturma

1. Keycloak Admin Console'a giriş yap (`http://localhost:8189/admin`)
2. Sol menüde **Realms** bölümüne git
3. **"Create Realm"** butonuna tıkla
4. Realm adı: `AuthServer` (application.yml'de yazanla uyumlu)
5. **Create** butonuna bas

#### 2) Client Oluşturma

1. **AuthServer** realm'ı seçili kıl
2. Sol menüde **Clients** bölümüne git
3. **"Create Client"** butonuna bas
4. Client ID: `gateway` (veya projen için uygun bir ad)
5. **Next** / **Save** ile devam et

#### 3) Client Ayarları

Oluşturulan client'ı aç ve şu ayarları yap:

- **Access Type (Authentication Flow):** `confidential` (backend-to-backend) veya `public` (SPA)
- **Valid Redirect URIs:** Gateway'in callback URL'si (producer/consumer token flow için)
  - Örnek: `http://localhost:8083/*`
- **Audience (Token):** Keycloak token'ında `aud` (audience) claim'ini set etmek isterseniz, Keycloak'ta advanced ayarlardan konfigüre edin

#### 4) Realm Roles ve User Mapping

1. **Realm Roles** sekmesine git
2. Roller oluştur: `microservices-admin`, `user` vb.
3. **Users** bölümünde kullanıcı oluştur
4. Kullanıcının **Role Mappings** sekmesinden rolleri ata

#### 5) JWKS Endpoint Doğrulama

Keycloak'ın public key'lerini sağladığı endpoint'i kontrol et:

```bash
curl http://localhost:8189/realms/AuthServer/protocol/openid-connect/certs
```

Çıktı şu şekilde olmalıdır:

```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "...",
      "use": "sig",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

### OAuth2.0 ve OpenID Connect Protokollerinin Özeti

#### OAuth2.0 Nedir?

OAuth2.0 (Open Authorization) bir **delegated authorization** protokolüdür. Kısaca:
- Kullanıcı kendi şifresi yerine **koruma altında bir token** ile servislere erişir
- Şifre sunuculara direkt gitmez; Keycloak gibi auth sunucusu tarafından kontrol edilir
- Token sınırlı ömürlü ve kapsam (scope) bazlıdır

#### OpenID Connect (OIDC) Nedir?

OpenID Connect, OAuth2.0'ın üzerine kurulu bir **authentication layer**'dır:
- OAuth2.0 + identity (kimlik) bilgisi
- JWT token üretir; token'ın içinde `sub` (subject), `email`, `name` gibi kullanıcı bilgileri bulunur
- Single Sign-On (SSO) ve federasyon senaryolarına uyumlu

#### Gateway'de Nasıl Kullanılıyor?

Gateway bu projede **Resource Server** rolü üstlenmiştir:
- Keycloak'tan gelen JWT token'ı doğrular
- Token geçerliyse, içindeki claimler (roller, kullanıcı ID) okunarak yetkilendirme yapılır
- `SecurityConfig` içinde role/endpoint bazlı kurallar tanımlanır

### Kullanılan Paketler (OAuth2 / OIDC İçin)

`pom.xml` içinde OAuth2 ve JWT desteği sağlayan bağımlılıklar:

```xml
<!-- OAuth2 Resource Server -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>

<!-- Spring Security (WebFlux uyumlu) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Test desteği -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

Kod seviyesinde kullanılan kütüphaneler:

- **`org.springframework.security.oauth2.jwt.Jwt`** → JWT token nesne modeli
- **`org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken`** → JWT tabanlı authentication token'ı
- **`org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter`** → Reactive WebFlux ortamında JWT converter
- **`ReactiveJwtAuthenticationConverterAdapter`** → `Jwt` → `AbstractAuthenticationToken` dönüşümü
- **`ServerHttpSecurity`** (WebFlux) → Reactive endpoint security kuralları

### SecurityConfig'de Keycloak Token İşleme

`src/main/java/com/mertalptekin/gateway/config/SecurityConfig.java` içinde:

```java
@Bean
public ReactiveJwtAuthenticationConverterAdapter reactiveJwtAuthenticationConverter() {
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthConverter = jwt -> {
        // Token içindeki realm_access.roles okunur
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            List<String> roles = (List<String>) realmAccess.get("roles");
            // Roller GrantedAuthority olarak dönüştürülür
            authorities.addAll(
                    roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList())
            );
        }
        return new JwtAuthenticationToken(jwt, authorities);
    };
    return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthConverter);
}
```

Bu converter, Keycloak token'ındaki `realm_access.roles` claim'ini okuyup Spring Security rollerine dönüştürür.

### İstemci Tarafında Token Alma ve Gönderme (Örnek)

Postman veya bir client uygulamada:

#### 1) Token Alma

```bash
curl -X POST \
  http://localhost:8189/realms/AuthServer/protocol/openid-connect/token \
  -d "client_id=gateway" \
  -d "client_secret=<CLIENT_SECRET>" \
  -d "grant_type=client_credentials"
```

Cevap:

```json
{
  "access_token": "eyJhbGc...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "token_type": "Bearer"
}
```

#### 2) Gateway'e İstek Gönderme

```bash
curl -H "Authorization: Bearer <access_token>" \
  http://localhost:8083/product-service/api/v1/products/details
```

Gateway `Authorization` header'ındaki token'ı alır, Keycloak public key'lerle doğrular ve geçerse isteği yönlendirir.

## Hangi Projelerin Gateway Bağımlılığı Var?

### 1) Doğrudan derleme (Maven) bağımlılığı

Bu repo içinde `gateway` artifact'ını Maven dependency olarak kullanan başka bir proje görünmüyor.
Yani **doğrudan compile-time bağımlılık yok**.

### 2) İşlevsel (runtime) bağımlılık

Aşağıdaki bileşenler gateway üzerinden erişim modeline bağımlıdır:

- **İstemciler (Postman / UI / dış servisler):** API çağrılarını gateway portundan (`8083`) yapar.
- **`order-service`:** Gateway route'u ile erişilen hedef servislerden biri.
- **`product-service`:** Gateway route'u ile erişilen hedef servislerden biri.

`docs/Spring Cloud Microservices.postman_collection.json` içinde de `localhost:8083` üzerinden örnek çağrılar bulunur.

## Gateway Projesinin Bağımlı Olduğu Projeler/Bileşenler

### 1) Repo içi servis bağımlılıkları

- **`order-service`** (route hedefi)
- **`product-service`** (route hedefi)
- **`eureka-server`** (service discovery için eureka client kullanımı)

Not: `application.yml` içinde eureka `defaultZone` açıkça yazılmasa da, eureka-client ile discovery mimarisi hedeflenmiştir.

### 2) Dış altyapı bağımlılıkları

- **Keycloak** (`http://localhost:8189/realms/AuthServer`) -> JWT doğrulama
- **Redis** (`localhost:6379`) -> rate limiter state
- **Zipkin** (`http://localhost:9411/api/v2/spans`) -> dağıtık iz sürme

## Güvenlik Akışı (Özet)

`SecurityConfig` içinde:

- `/actuator/**` -> `permitAll`
- `/product-service/**` -> `authenticated`
- `/order-service/**` -> şu an `permitAll` (yorum satırlarında role bazlı örnek bırakılmış)
- diğer tüm endpoint'ler -> `authenticated` (`anyExchange().authenticated()`)

Bu yapı, gateway'de merkezi authz/authn kuralı yönetimini gösterir.

### KeyResolver İyileştirmeleri

`GatewayApplication` içinde iki resolver tanımlıdır:

- `ipKeyResolver`: `remoteAddress` boş gelebilecek senaryolara karşı null-safe çalışır; boş durumda `unknown` döner.
- `userIdKeyResolver`: authenticated kullanıcı varsa `Principal#getName` değerini döner, yoksa `anonymous` döner.

Bu yaklaşım, rate limiter anahtar üretiminde NPE riskini azaltır ve IP bazlı/kimlik bazlı limit stratejileri arasında geçişi kolaylaştırır.

## Çalıştırma

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\gateway"
.\mvnw.cmd spring-boot:run
```

## Test

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\gateway"
.\mvnw.cmd test
```

## Notlar

- `gateway` modülü WebFlux tabanlıdır; bloklayıcı işlemlerden kaçınılmalıdır.
- `pom.xml` içinde bazı bağımlılıklar tekrar ediyor olabilir (ör. circuit breaker ve reactor-test). Temizlik için tekilleştirme yapılabilir.
- Production ortamında `permitAll` route'ları gözden geçirilmeli ve role bazlı erişimle sıkılaştırılmalıdır.

## Spring Cloud Gateway Önemli Özellikler

### 1) Predicates (Yönlendirme Kuralları)

Predicates, isteklerin hangi koşullarda iletilebileceğini tanımlar. `application.yml` içinde kullanılan örnek:

```yaml
- Path=/order-service/api/v1/**
- Path=/product-service/api/v1/**
```

Spring Cloud Gateway'in desteklediği predicate tipleri:

- **Path**: URL path'ine göre eşleştirme
- **Method**: HTTP METHOD'ına göre (GET, POST, PUT, DELETE vb.)
- **Header**: HTTP header değerlerine göre
- **Query**: Query parameter'larına göre
- **Host**: Host header'ına göre
- **Weight**: Yük dağıtımı (bir route'u % oranla diğerine yönlendir)
- **DateTime**: Belirli tarih/saat aralıklarında aktif
- **Cookie**: Cookie değerlerine göre

Örnek (extension):

```yaml
routes:
  - id: conditionalRoute
    uri: lb://order-service
    predicates:
      - Path=/order-service/**
      - Method=POST,GET
      - Header=X-Custom-Header,.*
      - Query=type,premium
```

### 2) Filters (İstek/Cevap Dönüştürme)

Filters, istekleri ve cevapları işlemek için middleware gibi davranır. `application.yml` içinde kullanılan örnek:

```yaml
- StripPrefix=1  # /order-service/... -> Hedef serviste /...
- RequestRateLimiter  # Rate limiting
- CircuitBreaker  # Hata toleransı
```

Spring Cloud Gateway'in desteklediği bazı filter tipleri:

- **StripPrefix**: URL path'inin başından N segment'i kaldır
- **PrefixPath**: URL path'inin başına önek ekle
- **RewritePath**: Regex ile path'i yeniden yaz
- **RequestRateLimiter**: Rate limiter uygula (Redis-backed)
- **CircuitBreaker**: Resilience4j ile circuit breaker
- **Retry**: Başarısız istekleri tekrar dene
- **RequestHeaderToRequestUri**: Header'daki bilgiyi URI'ya ekle
- **AddRequestHeader / AddResponseHeader**: Header ekle/değiştir
- **GatewayMetrics**: Prometheus metrikleri

Örnek (FilterFactory):

```yaml
- name: AddRequestHeader
  args:
    name: X-Request-ID
    value: "request-123"
- name: Retry
  args:
    retries: 3
    statuses: 502,503
    backoff:
      firstBackoff: 10ms
      maxBackoff: 50ms
      factor: 2
```

### 3) Rate Limiter (Redis-Backed)

Gateway'de `RequestRateLimiter` filter'ı, Redis'te hızlı karar verimi için token bucket algoritmasını kullanır.

`application.yml` içindeki yapılandırma:

```yaml
- name: RequestRateLimiter
  args:
    redis-rate-limiter.requestedTokens: 1  # Her istek kaç token tüketir
    redis-rate-limiter.replenishRate: 1    # Saniyede kaç token üretilir
    redis-rate-limiter.burstCapacity: 1    # İstemci kaç token biriktire bilir
    key-resolver: "#{@ipKeyResolver}"      # Token quota'sı kime atfedilir (IP, user ID vb.)
```

- `requestedTokens=1, replenishRate=1, burstCapacity=1`: Saniyede 1 istek izni
- IP başına rate limit uygulanır (her IP kendi quota'sına sahip)
- Redis'in uygun şekilde çalışması gerekir
- İhtiyaca göre `key-resolver` değeri `#{@userIdKeyResolver}` yapılarak kullanıcı bazlı limit uygulanabilir

### 4) Circuit Breaker (Resilience4j)

Gateway filter'ı olarak kullanılan circuit breaker, hedef servisin down olacağında fallback yanıt döner.

`application.yml` konfigürasyonu:

```yaml
- name: CircuitBreaker
  args:
    name: productServiceBreaker
    fallbackUri: forward:/fallback/product-service  # Hata durumunda gidilecek endpoint
    statusCodes:
      - "500"  # Hangi HTTP status'lar circuit breaker tetikler?

resilience4j:
  circuitbreaker:
    instances:
      productServiceBreaker:
        slidingWindowSize: 3              # Son 3 isteği takip et
        minimumNumberOfCalls: 3            # Karar vermek için en az 3 istek gerekli
        failureRateThreshold: 50           # %50 hata oranında circuit'i aç
        permittedNumberOfCallsInHalfOpenState: 2  # Half-open'da 2 isteği dene
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 30s       # Açık kaldığı süre
```

Bu özellik, cascade failure'ı (zincir hataları) önler ve sistemin bozulgan servisler nedeniyle tümüyle çökmesini engeller.

### 5) Request/Response Mutation (Değiştirme)

Gateway'de istekleri ve cevapları değiştirmek sık bir gereksinimdir:

```yaml
# Header Ekleme
- name: AddRequestHeader
  args:
    name: X-User-ID
    value: "${user_id}"

# Header Kaldırma
- name: RemoveRequestHeader
  args:
    name: X-Internal-Token

# Response Header Ekleme
- name: AddResponseHeader
  args:
    name: X-Request-Duration
    value: "ms"

# Path Yeniden Yazma
- name: RewritePath
  args:
    regexp: "^/old-api/(.*)$"
    replacement: "/new-api/$1"
```

## Production Ortamı İçin Dikkat Edilmesi Gereken Noktalar

### 1) HTTPS / TLS Konfigürasyonu

Production'da gateway'in SSL/TLS üzerinden çalışması zorunlu:

```yaml
server:
  port: 8443
  ssl:
    key-store: classpath:keystore.p12
    key-store-password: your_password
    key-store-type: PKCS12
    enabled: true
```

- Geçerli bir SSL sertifikası kullanın (self-signed değil)
- Keycloak da HTTPS olmalıdır; aksi takdirde `issuer-uri` ve `jwk-set-uri` başarısız olabilir
- Ters proxy (Nginx/Ingress) arkasındaysa `X-Forwarded-Proto` header'ını kontrol edin

### 2) Keycloak Token Yönetimi

Production'da token timeout ve refresh stratejisini belirleyin:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.production.com/realms/AuthServer
          jwk-set-uri: https://keycloak.production.com/realms/AuthServer/protocol/openid-connect/certs
```

Dikkat edilecek noktalar:

- Token cache'leme: Keycloak'ta public key'ler sık değişmez; gateway tarafında cache yapılabilir
- Token revocation: Login çıkışında token'ları iptal et
- Keycloak uptime: Keycloak down olursa token doğrulaması başarısız olur; HA kurulumu yap
- JWKS endpoint'in erişilebilir olduğundan emin ol (firewall, network)

### 3) Rate Limiter Ayarları

Production'da istemci profili ve SLA'ya göre ayarla:

```yaml
# Düık trafik ortamı
redis-rate-limiter.requestedTokens: 1
redis-rate-limiter.replenishRate: 100  # Saniyede 100 istek
redis-rate-limiter.burstCapacity: 200

# Premium plan için farklı route
- id: premiumRoute
  uri: lb://order-service
  predicates:
    - Path=/premium/**
    - Header=X-Plan-Type,premium
  filters:
    - StripPrefix=1
    - name: RequestRateLimiter
      args:
        redis-rate-limiter.requestedTokens: 1
        redis-rate-limiter.replenishRate: 1000
        redis-rate-limiter.burstCapacity: 2000
        key-resolver: "#{@userIdKeyResolver}"  # Kullanıcı ID'sine göre rate limit
```

**Redis High Availability:** Rate limiter Redis'e (ve Sentinel/Cluster) bağımlıdır. Single node Redis production ortamında yetersizdir.

### 4) Circuit Breaker Tuning

Production'da hedef servis karakteristiklerine göre ayarla:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      productServiceBreaker:
        slidingWindowSize: 10              # Daha fazla veri topla
        minimumNumberOfCalls: 5            # Karar vermek için 5 istek gerekli
        failureRateThreshold: 50           # %50 hata oranı
        slowCallRateThreshold: 80          # %80 slow call (>2s) oranı
        slowCallDurationThreshold: 2000    # 2 saniye üzeri slow call
        permittedNumberOfCallsInHalfOpenState: 3
        waitDurationInOpenState: 60s       # Circuit 1 dakika açık kalır
        failureExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
```

Fallback cevaplarının mantıklı olduğundan emin ol (hata mesajı, cache'lenmiş data vb.).

### 5) Logging ve Monitoring

Production'da şu komponentleri monitor et:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "health,info,prometheus,metrics,circuitbreakers"
  endpoint:
    health:
      show-details: when-authorized
  prometheus:
    metrics:
      export:
        enabled: true
  zipkin:
    tracing:
      endpoint: https://zipkin.production.com/api/v2/spans
  tracing:
    sampling:
      probability: 0.1  # %10 sampling (production'da az oranda trace)

logging:
  level:
    org.springframework.cloud.gateway: WARN
    org.springframework.security.oauth2: WARN
    io.github.resilience4j: WARN
```

- **Metrics:** Prometheus + Grafana ile gateway'in response time'ı, error rate'i, rate limiter rejection'larını izle
- **Traces:** Zipkin ile istek flow'unu ve latency'i takip et
- **Logs:** ELK (Elasticsearch/Kibana) ile centralized logging yap
- **Alerts:** Circuit breaker açılması, rate limit rejectionleri, auth failure'ları alarm olarak ayarla

### 6) CORS Konfigürasyonu

Cross-Origin istemleri kontrol altında tut:

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "https://client.production.com"
              - "https://admin.production.com"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
            allowedHeaders:
              - "*"
            allowCredentials: true
            maxAge: 3600
```

Production'da wildcard (`*`) kullanma; açıkça izin ver.

### 7) Load Balancing ve Redundancy

- **Multiple Gateway Instances:** Load balancer arkasında ≥2 gateway instance'ı çalıştır
- **Service Discovery:** Eureka'nın stabil ve HA kurulumu yap
- **Health Checks:** Load balancer'da gateway health endpoint'ini (`/actuator/health`) izle

### 8) Security Best Practices

- **Rate Limit Evasion:** IP spoofing riskini azalt; true client IP'yi doğru header'dan al
- **Request Timeout:** Veri kaybı riski için timeout'u makul set et:

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 10000  # ms
        response-timeout: 10000  # ms
```

- **Secret Management:** Client secret ve SSL key'leri environment variable'lardan oku; hardcode etme
- **Audit Logging:** Authentication/Authorization event'lerini log'la

## Özet

`gateway`, bu mimarinin trafik yönetim ve güvenlik katmanıdır. Servis keşfi, yönlendirme, güvenlik, rate limiting, circuit breaker ve izlenebilirlik yeteneklerini tek noktada toplar.
