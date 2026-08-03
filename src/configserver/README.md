# Config Server

Bu modül, mikroservislerin konfigürasyonlarını merkezi olarak sunan **Spring Cloud Config Server** uygulamasıdır.

## Amaç

`config-server`, servislerin ortam bazlı ayarlarını (dev/staging/prod gibi) **tek noktadan** yönetir.
Bu projede özellikle, **@RefreshScope** anotasyonuyla runtime'da konfigürasyon değişikliklerini 
**uygulamayı yeniden başlatmadan** gerçekleştirebilme imkanı sağlanmıştır.

Bu projede:

- Varsayılan çalışma profili `native` (lokal konfigürasyon dosyaları)
- İhtiyaç halinde `git` profil desteği mevcut (GitHub'daki config repository)
- `order-service`, `product-service`, `saga-service` Config Server'dan ayar almak için entegre
- **@RefreshScope** sayesinde deployment olmadan config güncellemesi

## Kullanılan Sürümler

`pom.xml` dosyasına göre:

- **Java:** 17
- **Spring Boot:** 4.0.1
- **Spring Cloud:** 2025.1.0
- **Build Tool:** Maven

## Kullanılan Teknolojiler

- **Spring Boot**
- **Spring Cloud Config Server**
- **Spring Web MVC**
- **Spring Boot Test / JUnit 5**
- **Maven Wrapper** (`mvnw`, `mvnw.cmd`)

## Konfigürasyon Dosya Yapısı

```
src/main/resources/
├── application.yml                    # Ortak ayarlar (port, uygulama adı)
├── application-git.yml                # Git profili konfigürasyonu
├── application-native.yml             # Native profili konfigürasyonu
├── order-service/                     # Order Service konfigürasyonları
│   ├── order-service.yml              # Global (tüm profiller)
│   ├── order-service-dev.yml          # Dev ortamı
│   ├── order-service-staging.yml      # Staging ortamı
│   └── order-service-prod.yml         # Production ortamı
├── product-service/                   # Product Service konfigürasyonları
│   ├── product-service-dev.yml        # Dev ortamı
│   ├── product-service-staging.yml    # Staging ortamı
│   └── product-service-prod.yml       # Production ortamı
└── saga-service/                      # Saga Service konfigürasyonları
    ├── saga-service-dev.yml           # Dev ortamı
    └── saga-service-staging.yml       # Staging ortamı
```

**Açıklamalar:**
- `application.yml`: Config Server'ın kendisinin temel ayarları
- `application-*.yml`: Profil-spesifik Config Server ayarları
- `order-service/`, `product-service/`, `saga-service/`: Client uygulamalar için konfigürasyon dosyaları

## Uygulama Konfigürasyonu

### 1) Ortak ayarlar (`application.yml`)

```yaml
server:
  port: 8085

spring:
  application:
    name: config-server
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:git}
```

- Uygulama adı: `config-server`
- Port: `8085`
- Profil seçimi: `SPRING_PROFILES_ACTIVE` ortam değişkeni ile yapılır
- Varsayılan profil: `git`

### 2) Git profili (`application-git.yml`)

```yaml
spring:
  config:
    activate:
      on-profile: git
  cloud:
    config:
      server:
        git:
          default-label: master
          uri: https://github.com/neominalbilisim/spring-cloud-config-repo
          search-paths: src/config/order-service-config
```

### 3) Native profili (`application-native.yml`)

```yaml
spring:
  config:
    activate:
      on-profile: native
  cloud:
    config:
      server:
        native:
          search-locations: classpath:/order-service,classpath:/product-service,classpath:/saga-service
```

Bu profil, `src/main/resources/` klasörü altındaki konfigürasyon dosyalarını direkt olarak kullanır.

#### Konfigürasyon Dosyası Örnekleri

**Dev Ortamı (`order-service-dev.yml`):**
```yaml
app:
  order-service:
    environment: dev
    version: 1.0.0-SNAPSHOT
    database:
      host: localhost
      port: 5432
      name: orderdb_dev
      username: dev_user
      password: dev_password
    apiKeys:
      productServiceUrl: http://localhost:8080
      requestTimeoutMs: 5000
    featureFlags:
      enableNewOrderProcess: false
      enableNotificationService: false
      enableDetailedLogging: true
```

**Staging Ortamı (`order-service-staging.yml`):**
```yaml
app:
  order-service:
    environment: staging
    version: 1.0.0-RC1
    database:
      host: postgres-staging.example.com
      port: 5432
      name: orderdb_staging
      username: staging_user
      password: "${DB_PASSWORD_STAGING:staging_password}"
    apiKeys:
      productServiceUrl: http://product-service-staging:8080
      requestTimeoutMs: 10000
    featureFlags:
      enableNewOrderProcess: true
      enableNotificationService: true
      enableDetailedLogging: false
```

**Production Ortamı (`order-service-prod.yml`):**
```yaml
app:
  order-service:
    environment: prod
    version: 1.0.0
    database:
      host: "${DB_HOST_PROD}"
      port: 5432
      name: orderdb_prod
      username: "${DB_USERNAME_PROD}"
      password: "${DB_PASSWORD_PROD}"
    apiKeys:
      productServiceUrl: http://product-service:8080
      requestTimeoutMs: 15000
    featureFlags:
      enableNewOrderProcess: true
      enableNotificationService: true
      enableDetailedLogging: false

logging:
  level:
    root: WARN
    com.mertalptekin: INFO
```

## Bağımlılıklar

Ana bağımlılıklar:

- `org.springframework.cloud:spring-cloud-config-server`
- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-webmvc-test` (test)

## Diğer Servislerle İlişki

- `config-server`, diğer servislerin konfigürasyonunu sunar.
- Kod seviyesinde doğrudan servis bağımlılığı yoktur.
- `order-service` içinde Config Client ayarları bulunduğu için bu servisten config çekebilir.

`order-service` içindeki ilgili ayarlar (özet):

```ini
spring.profiles.active=dev
spring.cloud.config.uri=http://localhost:8085
spring.config.import=optional:configserver:http://localhost:8085
```

## Çalıştırma

### Native profili ile çalıştırma (Önerilen - Lokal Konfigürasyon Dosyaları)

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\configserver"
$env:SPRING_PROFILES_ACTIVE="native"
.\mvnw.cmd spring-boot:run
```

**Avantajları:**
- Lokal dosyalarda config değişiklikleri hemen uygulanır
- Dış Git bağlantısına bağımlı değil
- Development/testing için ideal
- `/actuator/refresh` ile dinamik update imkanı

### Varsayılan (git profili)

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\configserver"
.\mvnw.cmd spring-boot:run
```

**Gereksinimler:**
- Internet erişimi
- GitHub'a erişim (ya da özel Git repo)

### Test

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\configserver"
.\mvnw.cmd test
```

### Tam Test Script'i (Order Service ile)

```powershell
# Proje ana dizininden çalıştır
.\test-dynamic-config.ps1
```

Bu script:
1. Config Server sağlığını kontrol eder
2. Order Service sağlığını kontrol eder
3. Dev profili konfigürasyonunu gösterir
4. Refresh endpoint'ini test eder
5. Güncellenmiş konfigürasyonu doğrular



Örnek sorgular:

- `GET http://localhost:8085/order-service/dev`
- `GET http://localhost:8085/order-service/prod`
- `GET http://localhost:8085/order-service/dev/master`

## Notlar

- Git profili için internet erişimi gerekir.
- Private Git repo kullanılırsa kimlik doğrulama ayarları (token/SSH) eklenmelidir.
- `application.properties` kaldırılmıştır; proje profil bazlı YML yapısı ile çalışır.

## Özet

## Dinamik Konfigürasyon Yönetimi (@RefreshScope)

### Problem Tanımı (Senaryo)

Bir sağlık platformasında **Hasta Servisi**, **Randevu Servisi** ve **Bildirim Servisi** bulunmaktadır. Tüm servislerin konfigürasyonu (veritabanı bağlantısı, API anahtarları, özellik flag'leri) her servisin uygulaması içinde hardcode edilmiş durumdadır. 

**Sorunlar:**
- Yeni ortama (staging) geçişte her servis için ayrı ayrı deployment yapılması gerekir
- Config değişikliğinde uygulama restart gerekir
- Ortamlar arasında config yönetimi zordur
- Dinamik feature flag'leri değiştiremezsiniz

### Çözüm: Spring Cloud Config Server + @RefreshScope

**Spring Cloud Config Server** kullanarak bu problemi şöyle çözüyoruz:

```
┌─────────────────────────────────────────────────────┐
│                 Config Server                       │
│  (Merkezi Konfigürasyon Deposu)                    │
│  - Git Repository veya Native(Classpath)            │
│  - order-service-dev.yml                           │
│  - order-service-staging.yml                       │
│  - product-service-dev.yml                         │
│  - etc.                                            │
└─────────────┬──────────────────────────────────────┘
              │ HTTP GET /order-service/staging
              ▼
┌─────────────────────────────────────────────────────┐
│          Order Service (Config Client)              │
│  @RefreshScope annotasyonlu Configuration Class     │
│  └─ DynamicConfigProperties                        │
│     ├─ Database (host, port, name, username)       │
│     ├─ ApiKeys (URLs, timeouts)                    │
│     └─ FeatureFlags (booleans)                     │
└─────────────────────────────────────────────────────┘
```

#### @RefreshScope Nasıl Çalışır?

1. **Uygulama Başlangıcında**: Spring, Config Server'dan konfigürasyonları okur
2. **Runtime'da**: Eğer config değişirse, `/actuator/refresh` endpoint'i çağrıldığında:
   - Config Server'dan yeni konfigürasyonlar yeniden okunur
   - @RefreshScope annotasyonlu bean'ler yeniden oluşturulur
   - **Uygulama restart olmadan** değerler güncellenir ✅

#### Faydalı Endpoint'ler

| Endpoint | Yöntem | Açıklama |
|----------|--------|----------|
| `/actuator/refresh` | POST | Config değişikliklerini uygulamak için çağır |
| `/actuator/configprops` | GET | Aktif konfigürasyon özelliklerini göster |
| `/actuator/env` | GET | Ortam değişkenlerini ve konfigürasyonları göster |
| `/api/v1/config/info` | GET | Uygulamaya özel aktif config bilgileri |
| `/api/v1/config/status` | GET | Config durumu ve versiyon bilgisi |

### Kullanım Adımları

#### 1) Config Server Başlat (Native Profil ile)

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\configserver"
$env:SPRING_PROFILES_ACTIVE="native"
.\mvnw.cmd spring-boot:run
# Config Server çalışıyor: http://localhost:8085
```

#### 2) Order Service Başlat

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\orderservice"
# İlk başlamada dev profili ile çalışacak
.\mvnw.cmd spring-boot:run
# Order Service çalışıyor: http://localhost:5001
```

#### 3) Mevcut Konfigürasyonları Kontrol Et

```powershell
# Browser veya Postman ile:
GET http://localhost:5001/api/v1/config/info

# Cevap örneği:
{
  "environment": "dev",
  "version": "1.0.0-SNAPSHOT",
  "database": {
    "host": "localhost",
    "port": 5432,
    "name": "orderdb_dev",
    "jdbcUrl": "jdbc:postgresql://localhost:5432/orderdb_dev"
  },
  "apiKeys": {
    "productServiceUrl": "http://localhost:8080",
    "requestTimeoutMs": 5000
  },
  "featureFlags": {
    "enableNewOrderProcess": false,
    "enableNotificationService": false,
    "enableDetailedLogging": true,
    "maxOrderRetry": 3
  }
}
```

#### 4) Config Server'da Konfigürasyon Dosyasını Düzenle

`order-service-staging.yml` dosyasını açıp şunları değiştir:

```yaml
app:
  order-service:
    featureFlags:
      enableNewOrderProcess: true  # false → true
      enableNotificationService: true  # false → true
      enableDetailedLogging: false  # true → false
    database:
      host: postgres-staging.example.com  # localhost → staging host
```

#### 5) Dinamik Refresh Tetikle

```powershell
# Postman veya curl ile Order Service'e POST isteği gönder:
POST http://localhost:5001/actuator/refresh

# Başarılı cevap:
[
  "config.client.version",
  "app.order-service.environment",
  "app.order-service.version",
  "app.order-service.database.host",
  "app.order-service.database.port",
  ...
]
```

#### 6) Güncellemeleri Doğrula

```powershell
# Aynı endpoint'i tekrar çağırıp yeni değerleri gör:
GET http://localhost:5001/api/v1/config/info

# Cevap artık staging konfigürasyonunu gösterir:
{
  "environment": "staging",
  "database": {
    "host": "postgres-staging.example.com",
    ...
  },
  "featureFlags": {
    "enableNewOrderProcess": true,
    "enableNotificationService": true,
    ...
  }
}
```

### Profil Değiştirerek Konfigürasyon Geçişi

Order Service'i **staging** profilinde başlatmak için:

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\orderservice"
$env:SPRING_PROFILES_ACTIVE="staging"
.\mvnw.cmd spring-boot:run
```

Config Server otomatik olarak `order-service-staging.yml` dosyasını sunacaktır.

### Konfigürasyon Dosya Yapısı

```
src/configserver/src/main/resources/
├── application.yml                  # Ortak ayarlar
├── application-native.yml           # Native profil (lokal konfigürasyon)
├── application-git.yml              # Git profil (GitHub repo)
├── order-service/
│   ├── order-service-dev.yml       # Dev ortamı
│   ├── order-service-staging.yml   # Staging ortamı
│   └── order-service-prod.yml      # Production ortamı
├── product-service/
│   ├── product-service-dev.yml
│   └── product-service-staging.yml
└── saga-service/
    └── saga-service-dev.yml
```

### Production'da Güvenlik

Production ortamında hassas bilgileri (database şifreleri, API anahtarları) 
göstermek için **ortam değişkenlerini** kullanın:

```yaml
# order-service-prod.yml
app:
  order-service:
    database:
      host: "${DB_HOST_PROD}"              # Ortam değişkeninden okunur
      username: "${DB_USERNAME_PROD}"
      password: "${DB_PASSWORD_PROD}"
    apiKeys:
      apiKeyExternal: "${API_KEY_PROD}"
```

Server'da değişken tanımla:
```powershell
$env:DB_HOST_PROD="prod-db.example.com"
$env:DB_USERNAME_PROD="prod_user"
$env:DB_PASSWORD_PROD="secure_password"
$env:API_KEY_PROD="prod_api_key_xyz"
```

### Git Profili Kullanımı

Production'da Config Server'ı Git repository'si ile kullanabilirsiniz:

```yaml
# application-git.yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/yourcompany/spring-cloud-config-repo
          search-paths: src/config/{application}
          default-label: main
```

Dizin yapısı:
```
spring-cloud-config-repo/
├── src/config/order-service/
│   ├── order-service-dev.yml
│   ├── order-service-staging.yml
│   └── order-service-prod.yml
└── src/config/product-service/
    └── product-service-dev.yml
```

### Avantajları

✅ **Tek noktadan yönetim**: Tüm servisler için merkezi konfigürasyon  
✅ **Deployment olmadan güncelleme**: Runtime'da konfigürasyon değişimi  
✅ **Ortam yönetimi kolaylaşır**: dev/staging/prod ayrımı net  
✅ **Feature flag'ler**: A/B testing ve gradual rollout  
✅ **Güvenlik**: Hassas bilgileri ortam değişkenlerine taşı  
✅ **Version kontrol**: Git ile config history'si tutun  

### Özet

`config-server`, bu mimaride merkezi konfigürasyon sunucusu olarak çalışmaktadır. 
Profil bazlı YML yaklaşımıyla (`application.yml`, `application-git.yml`, `application-native.yml`) 
hem Git tabanlı hem lokal (native) kullanım senaryoları desteklenir. 

### Özet

`config-server`, bu mimaride merkezi konfigürasyon sunucusu olarak çalışmaktadır. 
Profil bazlı YML yaklaşımıyla (`application.yml`, `application-git.yml`, `application-native.yml`) 
hem Git tabanlı hem lokal (native) kullanım senaryoları desteklenir. 

**@RefreshScope** sayesinde `/actuator/refresh` endpoint'i çağrıldığında servisleri 
restart etmeden konfigürasyonları dinamik olarak güncelleyebilirsiniz. Bu özellikle 
staging ve production ortamlarında deployment sürelerini önemli ölçüde azaltır.

---

## Faydalı Kaynaklar

- **Order Service Integration:** [`src/orderservice/README.md`](../orderservice/README.md#dinamik-konfigürasyon-yönetimi-refreshscope)
- **Dynamic Config Code:** [`DynamicConfigProperties.java`](../orderservice/src/main/java/com/mertalptekin/orderservice/config/DynamicConfigProperties.java)
- **Config Controller:** [`ConfigController.java`](../orderservice/src/main/java/com/mertalptekin/orderservice/controller/ConfigController.java)
- **Test Script:** [`test-dynamic-config.ps1`](../../test-dynamic-config.ps1)


