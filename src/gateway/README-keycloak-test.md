# Gateway Keycloak Test Rehberi

Bu doküman, `gateway` modülündeki Keycloak tabanlı JWT doğrulamasını **adım adım test etmek** için hazırlanmıştır. Hem **Keycloak tarafında** yapılması gereken ayarlar hem de **Postman tarafında** izlenecek akış burada açıklanır.

Bu rehberin amacı şunları netleştirmektir:

- Keycloak üzerinde hangi realm, client, user ve role ayarlarının yapılacağı
- Gateway'in hangi endpoint'inin authentication istediği
- Postman ile nasıl token alınacağı
- Alınan token ile gateway üzerinden nasıl istek atılacağı
- Hata durumlarında nerelerin kontrol edilmesi gerektiği

---

## 1. Testin Amacı

`gateway`, `spring-boot-starter-oauth2-resource-server` ile JWT doğrulayan bir **Resource Server** olarak çalışır.

Mevcut güvenlik akışına göre:

- `/actuator/**` → kimlik doğrulama istemez
- `/product-service/**` → **authenticated** ister
- `/order-service/**` → şu an `permitAll`
- diğer endpoint'ler → **authenticated** ister

Bu nedenle Keycloak testini doğrulamak için en uygun route:

- `POST http://localhost:8083/product-service/api/v1/products/details`

Bu endpoint'e:

- **tokensız** istek atıldığında `401 Unauthorized`
- **geçerli token ile** istek atıldığında gateway doğrulama yapıp isteği `product-service`'e yönlendirir

beklenir.

---

## 2. Mevcut Proje Bilgileri

### Gateway JWT ayarları

`src/main/resources/application.yml` içinde şu ayarlar tanımlıdır:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8189/realms/AuthServer
          jwk-set-uri: http://localhost:8189/realms/AuthServer/protocol/openid-connect/certs
```

### Gateway route bilgisi

Aynı dosyada korunan route şu şekildedir:

```yaml
- id: productClient
  uri: lb://product-service
  predicates:
    - Path=/product-service/api/v1/**
```

### SecurityConfig bilgisi

`src/main/java/com/mertalptekin/gateway/config/SecurityConfig.java` içinde:

```text
.pathMatchers("/product-service/**").authenticated()
```

Bu yüzden testte `product-service` route'u kullanılmalıdır.

---

## 3. Önemli Not: Port Uyumsuzluğu

Projede şu anda iki farklı Keycloak port bilgisi görülüyor:

- `gateway` konfigürasyonu → `8189`
- Docker Compose ve Postman koleksiyonu → `8180`

`docs/docker/docker/keycloak/docker-compose.yml` dosyasında:

```yaml
ports:
  - "8180:8080"
```

Bu durumda **gerçek çalışan Keycloak portu büyük olasılıkla `8180`** olacaktır.

### Teste başlamadan önce mutlaka portları eşitleyin

İki seçenekten birini kullanın:

#### Seçenek A — Gateway'i `8180` portuna göre çalıştırın

`gateway` içindeki `application.yml` dosyasındaki Keycloak URL'lerini `8180` ile eşitleyin.

#### Seçenek B — Keycloak'ı `8189` portunda yayınlayın

Docker Compose port mapping'ini değiştirin.

> En risksiz yaklaşım, Docker Compose ve Postman koleksiyonuyla uyumlu olduğu için `8180` portunu esas almaktır.

Bu rehberde örnekler **`8180`** üzerinden verilmiştir.

---

## 4. Önkoşullar

Teste başlamadan önce aşağıdaki bileşenlerin ayakta olması gerekir:

- Keycloak
- Redis
- Kafka
- Eureka Server
- Product Service
- Gateway

> **Not:** Bu testte Redis, Keycloak için değil `gateway` içindeki `RequestRateLimiter` filtresi için gereklidir. Gateway, istek sayaçlarını ve token bucket durumunu Redis üzerinde tuttuğu için Redis kapalıysa rate limiting davranışı düzgün çalışmayabilir ve gateway tarafında hata görülebilir.

### Keycloak'ı başlatma

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\docs\docker\docker\keycloak"
docker-compose up -d
```

### Redis'i başlatma

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\docs\docker\docker\redis"
docker-compose up -d
```

`gateway` projesinde `product-service` route'u üzerinde Redis tabanlı `RequestRateLimiter` kullanılmaktadır. Bu yapı token bucket algoritması ile çalışır ve:

- her isteğin kaç token tüketeceğini,
- saniyede kaç yeni token üretileceğini,
- istemcinin ne kadar burst trafik gönderebileceğini

Redis üzerinde takip eder. Bu nedenle Redis olmadan gateway'in rate limiter filtresi sağlıklı çalışmaz.

### Kafka'yı başlatma

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\docs\docker\docker\kafka"
docker-compose up -d
```

### Eureka Server'ı başlatma

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\eureka-server"
.\mvnw.cmd spring-boot:run
```

### Product Service'i başlatma

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\product-service"
.\mvnw.cmd spring-boot:run
```

> **Önemli not:** `product-service`, `application.yml` içinde Kafka binder (`spring.cloud.stream.kafka.binder.brokers`) kullandığı için sağlıklı şekilde başlatılmadan önce Kafka servisinin ayakta olması gerekir. Kafka kapalıysa uygulama Spring Boot Admin üzerinde `DOWN` görünebilir ve event binding/health kontrolleri başarısız olabilir.

### Gateway'i başlatma

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\gateway"
.\mvnw.cmd spring-boot:run
```

---

## 5. Keycloak Tarafında Yapılacaklar

### 5.1 Admin Console'a giriş

Tarayıcıdan aşağıdaki adresi açın:

```text
http://localhost:8180/admin
```

Varsayılan bilgiler:

- Kullanıcı adı: `admin`
- Şifre: `admin`

---

### 5.2 Realm oluşturma

Gateway konfigürasyonunda şu realm bekleniyor:

```text
AuthServer
```

Adımlar:

1. Sol üstten realm menüsünü açın
2. **Create realm** seçin
3. Realm adı olarak `AuthServer` girin
4. Kaydedin

> Realm adı, `issuer-uri` içindeki realm adıyla birebir aynı olmalıdır.

---

### 5.3 Client oluşturma

Gateway testinde token üretmek için bir client gerekir.

Önerilen client bilgisi:

- Client ID: `test-client`

Adımlar:

1. `AuthServer` realm'ındayken **Clients** menüsüne girin
2. **Create client** seçin
3. Client ID olarak `test-client` yazın
4. Client type için `OpenID Connect` seçin
5. Kaydedin

---

### 5.4 Client ayarları

Postman ile kullanıcı adı/şifre üzerinden token almak istiyorsanız aşağıdaki ayarlar önemlidir:

- **Client authentication**: Açık olabilir
- **Authorization enabled**: İhtiyaca göre açık kalabilir
- **Direct access grants**: **Enabled** olmalıdır
- **Service accounts roles**: İsterseniz ayrıca açabilirsiniz

#### Neden `Direct access grants` gerekli?

Postman koleksiyonundaki token alma isteği `grant_type=password` kullanır. Bu akışın çalışması için client üzerinde **Direct Access Grants** açık olmalıdır.

---

### 5.5 Client secret alma

Eğer client confidential ise bir `client_secret` gerekir.

Adımlar:

1. Oluşturduğunuz client'ı açın
2. **Credentials** sekmesine girin
3. Üretilen secret değerini kopyalayın
4. Postman'de `client_secret` alanına ekleyin

---

### 5.6 Role oluşturma

Gateway şu anda `/product-service/**` için yalnızca `authenticated` kontrolü yapıyor. Yani bu test için role zorunlu değil. Yine de ileride role bazlı test yapmak için role oluşturmanız faydalıdır.

Önerilen role örnekleri:

- `microservices-admin`
- `user`

Adımlar:

1. **Realm roles** menüsüne gidin
2. `microservices-admin` ve/veya `user` rollerini oluşturun

> `SecurityConfig` içinde ileride `.hasAuthority("microservices-admin")` aktif edilirse, token içindeki `realm_access.roles` alanında bu rolün bulunması gerekir.

---

### 5.7 Kullanıcı oluşturma

Postman ile password grant testi için bir kullanıcı gerekir.

Önerilen örnek kullanıcı:

- Kullanıcı adı: `ali`
- Şifre: `P@ssword1`

Adımlar:

1. **Users** menüsüne girin
2. **Add user** seçin
3. Username olarak `ali` girin
4. Kaydedin
5. **Credentials** sekmesine gidin
6. Şifre olarak `P@ssword1` girin
7. `Temporary` kapalı olacak şekilde kaydedin

---

### 5.8 Role atama

1. Kullanıcıyı açın
2. **Role mapping** bölümüne girin
3. `microservices-admin` veya `user` rolünü kullanıcıya atayın

Bu adım, role bazlı güvenlik testleri için gereklidir.

---

## 6. Keycloak Kurulumunu Doğrulama

### 6.1 OpenID yapılandırmasını kontrol etme

Tarayıcı veya terminalden:

```powershell
curl http://localhost:8180/realms/AuthServer/.well-known/openid-configuration
```

Beklenen durum:

- JSON döner
- içinde `issuer`, `token_endpoint`, `jwks_uri` alanları görünür

### 6.2 JWKS endpoint'ini kontrol etme

```powershell
curl http://localhost:8180/realms/AuthServer/protocol/openid-connect/certs
```

Beklenen durum:

- JSON içinde `keys` dizisi görünür
- Gateway bu anahtarları kullanarak JWT imzasını doğrular

---

## 7. Postman Tarafında Yapılacaklar

Bu projede zaten bir Postman koleksiyonu vardır:

- `docs/Spring Cloud Microservices.postman_collection.json`

Koleksiyonda üç önemli bölüm bulunur:

- `AuthServer`
- `Gateway`
- `OrderService`

Keycloak testi için asıl kullanacağınız istekler:

- `AuthServer / ConnectToken`
- `Gateway / Product Service (GetOrderedProducts FROM GW)`

---

## 8. Postman ile Token Alma

### 8.1 İstek bilgileri

Method:

```text
POST
```

URL:

```text
http://localhost:8180/realms/AuthServer/protocol/openid-connect/token
```

Body türü:

```text
x-www-form-urlencoded
```

Gönderilecek alanlar:

| Alan | Değer | Açıklama |
|---|---|---|
| `grant_type` | `password` | Resource owner password flow |
| `username` | `ali` | Keycloak kullanıcısı |
| `password` | `P@ssword1` | Kullanıcı şifresi |
| `client_id` | `test-client` | Oluşturduğunuz client |
| `client_secret` | `<CLIENT_SECRET>` | Client secret |
| `scope` | `openid profile` | Temel OIDC scope'ları |

### 8.2 Başarılı cevap örneği

```json
{
  "access_token": "eyJhbGciOi...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "refresh_token": "eyJhbGciOi...",
  "token_type": "Bearer",
  "scope": "openid profile"
}
```

### 8.3 Postman'de saklama önerisi

Postman ortam değişkeni tanımlayın:

- `accessToken`

Sonra response içindeki `access_token` değerini bu değişkene kaydedin.

İsterseniz Tests sekmesine şu script'i ekleyebilirsiniz:

```javascript
const json = pm.response.json();
pm.environment.set("accessToken", json.access_token);
```

---

## 9. Postman ile Gateway Üzerinden Korumalı Endpoint Testi

### 9.1 İlk test: tokensız istek

Aşağıdaki isteği **Authorization olmadan** gönderin:

```text
POST http://localhost:8083/product-service/api/v1/products/details
```

Body:

```json
{
  "ProductIds": ["1"]
}
```

Beklenen sonuç:

- HTTP `401 Unauthorized`

Bu sonuç, gateway'in gerçekten authentication istediğini gösterir.

> Art arda çok hızlı istek gönderirseniz, authentication doğru olsa bile gateway üzerindeki Redis tabanlı rate limiter nedeniyle `429 Too Many Requests` cevabı alabilirsiniz.

---

### 9.2 İkinci test: Bearer token ile istek

Aynı isteği bu kez aşağıdaki header ile gönderin:

```text
Authorization: Bearer {{accessToken}}
```

Method ve URL:

```text
POST http://localhost:8083/product-service/api/v1/products/details
```

Body:

```json
{
  "ProductIds": ["1"]
}
```

Beklenen sonuç:

- HTTP `200 OK` veya uygulamanın iş kuralına göre başka bir business response
- Gateway token'ı doğrular
- İstek `product-service`'e route edilir

> `ProductIds` içine iki eleman gönderirseniz, `product-service` içindeki demo davranışı nedeniyle bilinçli hata senaryosu tetiklenebilir.

---

## 10. Postman Koleksiyonu ile Test Akışı

Mevcut koleksiyonu kullanmak isterseniz şu sırayı izleyin:

### Adım 1 — `AuthServer / ConnectToken`

Bu istekle access token alın.

Kontrol edin:

- URL `8180` mi?
- `realm` adı `AuthServer` mı?
- `client_id` doğru mu?
- `client_secret` güncel mi?

### Adım 2 — `Gateway / Product Service (GetOrderedProducts FROM GW)`

Bu istekte Bearer token alanına `{{accessToken}}` verin.

Body örneği:

```json
{
  "ProductIds": ["1"]
}
```

### Adım 3 — Sonucu yorumlayın

- `401` → token yok, geçersiz ya da gateway issuer uyuşmuyor
- `403` → authentication var ama authorization kuralı engelliyor
- `5xx` → gateway downstream servise ulaşamıyor olabilir
- `200` → Keycloak + gateway JWT doğrulaması başarılı

---

## 11. JWT İçeriğinde Neye Bakılmalı?

Gateway, `SecurityConfig` içinde token'dan `realm_access.roles` alanını okuyup authority üretir.

Yani örnek token payload'ında şuna benzer bir alan görmek faydalıdır:

```json
{
  "preferred_username": "ali",
  "realm_access": {
    "roles": [
      "default-roles-authserver",
      "user",
      "microservices-admin"
    ]
  }
}
```

### Bu neden önemli?

Şu an `/product-service/**` için sadece `authenticated()` kontrolü var. Ancak ileride aşağıdaki gibi bir kural aktif edilirse:

```text
.pathMatchers("/order-service/**").hasAuthority("microservices-admin")
```

kullanıcının token'ında gerçekten `microservices-admin` rolü bulunmalıdır.

---

## 12. Önerilen Test Senaryoları

### Senaryo 1 — Token yok

- İstek: `POST /product-service/api/v1/products/details`
- Authorization: yok
- Beklenen: `401`

### Senaryo 2 — Geçerli token var

- İstek: aynı endpoint
- Authorization: geçerli Bearer token
- Beklenen: `200`

### Senaryo 3 — Yanlış issuer

- Gateway `issuer-uri` farklı porta bakıyor
- Beklenen: token doğrulama başarısız olur
- Çoğu durumda `401` veya başlangıçta JWKS erişim hatası görülür

### Senaryo 4 — Yanlış client secret

- Token alma isteği başarısız olur
- Beklenen: `400` veya `401`

### Senaryo 5 — User mevcut ama şifre yanlış

- Token alınamaz
- Beklenen: `invalid_grant`

### Senaryo 6 — Direct Access Grants kapalı

- `grant_type=password` akışı başarısız olur
- Beklenen: token endpoint hata döner

---

## 13. Sorun Giderme

### Hata: `401 Unauthorized`

Olası nedenler:

- Bearer token gönderilmedi
- Token süresi doldu
- Gateway yanlış `issuer-uri` kullanıyor
- `jwk-set-uri` erişilemiyor
- Realm adı uyuşmuyor

Kontrol listesi:

1. Gateway ile Keycloak aynı porta mı bakıyor?
2. Realm adı gerçekten `AuthServer` mı?
3. `curl http://localhost:8180/realms/AuthServer/protocol/openid-connect/certs` çalışıyor mu?
4. Token içindeki `iss` claim'i gateway `issuer-uri` ile eşleşiyor mu?

---

### Hata: `403 Forbidden`

Olası nedenler:

- Authentication başarılı ama yetki kuralı yetersiz
- Role token içinde yok
- `hasAuthority(...)` gibi bir kural devreye alınmış olabilir

Kontrol edin:

- Token payload içinde `realm_access.roles` var mı?
- İlgili role kullanıcıya gerçekten atanmış mı?

---

### Hata: `429 Too Many Requests`

Olası nedenler:

- Redis tabanlı rate limiter devreye girdi
- Aynı IP üzerinden çok kısa sürede birden fazla istek gönderildi
- Postman Collection Runner ile seri test yapılırken istek limiti aşıldı

Kontrol edin:

- Redis ayakta mı? (`localhost:6379`)
- Gateway içindeki `RequestRateLimiter` ayarları çok düşük mü?
- Test istekleri arasında kısa bir bekleme koymak gerekiyor mu?

Bu hata, çoğu zaman Keycloak veya JWT doğrulama problemi değil, gateway üzerindeki trafik sınırlama davranışıdır.

---

### Hata: Token alınamıyor

Olası nedenler:

- `client_secret` yanlış
- `Direct Access Grants` kapalı
- kullanıcı şifresi yanlış
- realm yanlış
- URL yanlış portta

---

### Hata: Gateway açılıyor ama JWT doğrulayamıyor

Olası nedenler:

- Keycloak ayakta değil
- `issuer-uri` yanlış
- `jwk-set-uri` yanlış
- Docker port mapping ile uygulama ayarı uyuşmuyor

---

## 14. Hızlı Kontrol Özeti

Aşağıdaki checklist ile test ortamını hızlıca doğrulayabilirsiniz:

- [ ] Keycloak ayakta mı? (`http://localhost:8180/admin`)
- [ ] Redis ayakta mı? (`localhost:6379`)
- [ ] Kafka ayakta mı? (`localhost:29092`)
- [ ] Realm adı `AuthServer` mı?
- [ ] Client adı `test-client` mi?
- [ ] `Direct Access Grants` açık mı?
- [ ] Kullanıcı `ali` oluşturuldu mu?
- [ ] Kullanıcı şifresi `P@ssword1` olarak set edildi mi?
- [ ] Client secret Postman'e doğru girildi mi?
- [ ] Gateway, Keycloak için doğru porta mı bakıyor?
- [ ] `product-service` ve `gateway` ayakta mı?
- [ ] `product-service`, Admin Server üzerinde `UP` görünüyor mu?
- [ ] Tekrarlı testlerde beklenmedik `429 Too Many Requests` alınıyor mu?
- [ ] Tokensız istek `401` dönüyor mu?
- [ ] Tokenlı istek başarılı dönüyor mu?

---

## 15. Özet

Bu projede Keycloak testi için ana mantık şudur:

1. Keycloak üzerinde `AuthServer` realm'ını oluştur
2. `test-client` adlı OIDC client tanımla
3. `ali` kullanıcısını ve gerekirse rollerini oluştur
4. Postman ile token al
5. Bu token'ı gateway üzerindeki korumalı `product-service` endpoint'ine gönder
6. Tokensız istekle `401`, tokenlı istekle başarılı sonuç alarak güvenlik akışını doğrula

Bu akış doğrulandığında, `gateway` modülündeki:

- JWT doğrulama
- Keycloak entegrasyonu
- Resource Server davranışı
- route koruma mantığı

başarılı şekilde test edilmiş olur.

