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

Bu yapı, gateway'de merkezi authz/authn kuralı yönetimini gösterir.

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

## Özet

`gateway`, bu mimarinin trafik yönetim ve güvenlik katmanıdır. Servis keşfi, yönlendirme, güvenlik, rate limiting, circuit breaker ve izlenebilirlik yeteneklerini tek noktada toplar.
