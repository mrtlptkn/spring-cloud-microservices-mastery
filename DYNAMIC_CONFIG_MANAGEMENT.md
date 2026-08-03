# Spring Cloud Dinamik Konfigürasyon Yönetimi (@RefreshScope)

## Özet

Bu dokuman, Spring Cloud Config Server ve @RefreshScope kullanarak, **deployment yapmadan** 
runtime'da konfigürasyon değişikliklerini nasıl uygulanacağını açıklar.

---

## Problem Senariosu

### Durum
Bir sağlık platformasında **Hasta Servisi**, **Randevu Servisi** ve **Bildirim Servisi** bulunmaktadır.

**Sorunlar:**
1. ❌ Veritabanı bağlantı bilgileri hardcoded
2. ❌ API anahtarları hardcoded
3. ❌ Feature flag'ler uygulama içinde sabit
4. ❌ Yeni ortama (staging) geçişte her servis için ayrı deployment gerekli
5. ❌ Config değişiklikleri için uygulama restart gerekmektedir

---

## Çözüm: Spring Cloud Config Server + @RefreshScope

### Mimari

```
┌────────────────────────────────────────────────┐
│         Config Server (Port 8085)              │
│                                                │
│  Native Profil (Lokal Dosyalar)              │
│  ├── order-service-dev.yml                   │
│  ├── order-service-staging.yml               │
│  ├── order-service-prod.yml                  │
│  ├── product-service-dev.yml                 │
│  └── saga-service-dev.yml                    │
└────────────────────────────────────────────────┘
          ▲                          ▲
          │ GET /order-service/dev  │
          │                         │
          │                         │
    ┌─────────────────┐      ┌────────────────┐
    │ Order Service   │      │ Product Service│
    │ (Port 5001)     │      │ (Port 5002)    │
    │                 │      │                │
    │ @RefreshScope   │      │ @RefreshScope  │
    │ Config          │      │ Config         │
    └─────────────────┘      └────────────────┘
            │
            │ POST /actuator/refresh
            │
    ┌─────────────────────────────────────┐
    │  Refresh Triggered!                 │
    │  ✅ No Restart Needed              │
    │  ✅ Config Updated in Seconds      │
    └─────────────────────────────────────┘
```

---

## Temel Komponentler

### 1. Config Server (`configserver`)

**Sorumluluk:** Tüm servislerin konfigürasyonlarını merkezi olarak sunmak

**Profiller:**
- `native`: Lokal dosyalardan config okur (src/main/resources/)
- `git`: GitHub veya private Git repository'den okur

**Başlatma:**
```powershell
cd src/configserver
$env:SPRING_PROFILES_ACTIVE="native"
.\mvnw.cmd spring-boot:run
```

**Config Server Endpoint'leri:**
```
GET /order-service/dev       → order-service-dev.yml dosyasını döner
GET /order-service/staging   → order-service-staging.yml dosyasını döner
GET /order-service/prod      → order-service-prod.yml dosyasını döner
```

### 2. Config Client + @RefreshScope (`orderservice`)

**Sorumluluk:** Config Server'dan konfigürasyon almak ve @RefreshScope ile dinamik güncelleme

**Ana Sınıf: DynamicConfigProperties**
```java
@Component
@RefreshScope  // ← Refresh endpoint çağrıldığında yeniden oluşturulur
@ConfigurationProperties(prefix = "app.order-service")
public class DynamicConfigProperties {
    private Database database;      // host, port, name, username, password
    private ApiKeys apiKeys;        // URLs ve timeout değerleri
    private FeatureFlags featureFlags;  // enableNewOrderProcess, enableNotificationService
    private String environment;     // dev, staging, prod
    private String version;         // Versiyon bilgisi
}
```

**Başlatma:**
```powershell
cd src/orderservice
$env:SPRING_PROFILES_ACTIVE="dev"
.\mvnw.cmd spring-boot:run
```

### 3. Konfigürasyon Dosyaları

**Dizin Yapısı:**
```
src/configserver/src/main/resources/
├── order-service/
│   ├── order-service.yml          # Global (tüm profiller)
│   ├── order-service-dev.yml      # Dev profili
│   ├── order-service-staging.yml  # Staging profili
│   └── order-service-prod.yml     # Production profili
├── product-service/
│   ├── product-service-dev.yml
│   ├── product-service-staging.yml
│   └── product-service-prod.yml
└── saga-service/
    ├── saga-service-dev.yml
    └── saga-service-staging.yml
```

**Örnek: order-service-dev.yml**
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
      notificationServiceUrl: http://localhost:9000
      requestTimeoutMs: 5000
    
    featureFlags:
      enableNewOrderProcess: false
      enableNotificationService: false
      enableDetailedLogging: true
      maxOrderRetry: 3
```

---

## Kullanım Adımları

### Adım 1: Servisleri Başlat

**Terminal 1 - Config Server:**
```powershell
cd src/configserver
$env:SPRING_PROFILES_ACTIVE="native"
.\mvnw.cmd spring-boot:run
# Çalışıyor: http://localhost:8085
```

**Terminal 2 - Order Service (Dev Profili):**
```powershell
cd src/orderservice
$env:SPRING_PROFILES_ACTIVE="dev"
.\mvnw.cmd spring-boot:run
# Çalışıyor: http://localhost:5001
```

### Adım 2: Mevcut Konfigürasyonları Kontrol Et

```bash
curl http://localhost:5001/api/v1/config/info

Cevap:
{
  "environment": "dev",
  "version": "1.0.0-SNAPSHOT",
  "database": {
    "host": "localhost",
    "port": 5432,
    "name": "orderdb_dev"
  },
  "featureFlags": {
    "enableNewOrderProcess": false,
    "enableNotificationService": false,
    "maxOrderRetry": 3
  }
}
```

### Adım 3: Config Server'da Dosyayı Güncelleyin

File: `src/configserver/src/main/resources/order-service/order-service-staging.yml`

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
      password: staging_password
    apiKeys:
      productServiceUrl: http://product-service-staging:8080
      requestTimeoutMs: 10000
    featureFlags:
      enableNewOrderProcess: true        # ← Değişti!
      enableNotificationService: true    # ← Değişti!
      enableDetailedLogging: false
      maxOrderRetry: 5
```

### Adım 4: Refresh Tetikle

```bash
curl -X POST http://localhost:5001/actuator/refresh

Cevap (Güncellenmiş Property Listesi):
[
  "config.client.version",
  "app.order-service.environment",
  "app.order-service.version",
  "app.order-service.database.host",
  "app.order-service.database.port",
  "app.order-service.apiKeys.productServiceUrl",
  "app.order-service.featureFlags.enableNewOrderProcess",
  "app.order-service.featureFlags.enableNotificationService",
  ...
]
```

### Adım 5: Güncellemeleri Doğrula

```bash
curl http://localhost:5001/api/v1/config/info

Cevap (Yeni Staging Konfigürasyonu):
{
  "environment": "staging",
  "version": "1.0.0-RC1",
  "database": {
    "host": "postgres-staging.example.com",      # ← Değişti!
    "port": 5432,
    "name": "orderdb_staging"
  },
  "featureFlags": {
    "enableNewOrderProcess": true,              # ← Değişti!
    "enableNotificationService": true,          # ← Değişti!
    "maxOrderRetry": 5                          # ← Değişti!
  }
}
```

✅ **Başarılı!** Uygulama restart olmadan konfigürasyonlar güncellenmiş.

---

## Test Script'i

Tüm adımları otomatik olarak test etmek için:

```powershell
.\test-dynamic-config.ps1
```

Bu script şunları yapar:
1. Config Server sağlığını kontrol eder
2. Order Service sağlığını kontrol eder
3. Dev profili konfigürasyonunu gösterir
4. /actuator/refresh endpoint'ini test eder
5. Güncellenmiş konfigürasyonu doğrular

---

## Ortam Bazlı Konfigürasyon

### Dev Ortamı

**Başlatma:**
```powershell
$env:SPRING_PROFILES_ACTIVE="dev"
.\mvnw.cmd spring-boot:run
```

**Karakteristikleri:**
- ✅ Lokal database (localhost:5432)
- ✅ Detaylı logging (DEBUG)
- ✅ Feature flag'ler disable
- ✅ Kısa timeout (5 saniye)

### Staging Ortamı

**Başlatma:**
```powershell
$env:SPRING_PROFILES_ACTIVE="staging"
.\mvnw.cmd spring-boot:run
```

**Karakteristikleri:**
- ✅ Staging database (postgres-staging.example.com)
- ✅ Normal logging (INFO)
- ✅ Feature flag'ler enable
- ✅ Orta timeout (10 saniye)
- ✅ Production'a benzer ayarlar testi

### Production Ortamı

**Başlatma:**
```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
.\mvnw.cmd spring-boot:run
```

**Karakteristikleri:**
- ⚠️ Ortam değişkenlerinden hassas bilgileri okur
- ✅ Minimal logging (WARN)
- ✅ Feature flag'ler enable
- ✅ Uzun timeout (15 saniye)
- ✅ Yüksek availability ayarları

---

## Production'da Güvenlik

Hassas bilgileri (database şifreleri, API key'ler) ortam değişkenlerine taşıyın:

**Konfigürasyon Dosyası:**
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

**Server'da Ortam Değişkenlerini Tanımla:**
```bash
export DB_HOST_PROD="prod-db.example.com"
export DB_USERNAME_PROD="prod_user"
export DB_PASSWORD_PROD="secure_password_here"
export API_KEY_PROD="api_key_value_here"

java -jar order-service.jar
```

---

## Gerçek Senaryolar

### Senaryo 1: A/B Testing

**Dev'de test ettiğiniz yeni özelliği bir gruba sunmak:**

1. **Config'de Feature Flag'i Aç:**
   ```yaml
   featureFlags:
     enableNewOrderProcess: true
   ```

2. **Refresh Tetikle:**
   ```bash
   curl -X POST http://localhost:5001/actuator/refresh
   ```

3. **Anında Yayına Hazır!** ✅

### Senaryo 2: Database Migrasyonu

**Staging'de eski database'den yeni database'e geçiş:**

1. **Config'de Veritabanı Adını Değiştir:**
   ```yaml
   database:
     host: new-db-staging.example.com
     name: orderdb_new_staging
   ```

2. **Refresh Tetikle:**
   ```bash
   curl -X POST http://localhost:5001/actuator/refresh
   ```

3. **Yeni Database'e Bağlanıyor!** ✅ (Restart olmadan)

### Senaryo 3: Timeout Ayarları

**Production'da yavaş API'ye bağlantı problemi:**

1. **Timeout'u Artır:**
   ```yaml
   apiKeys:
     requestTimeoutMs: 15000  # 15 saniye
   ```

2. **Refresh Tetikle:**
   ```bash
   curl -X POST http://localhost:5001/actuator/refresh
   ```

3. **Artan Timeout Uygulanıyor!** ✅

---

## Avantajları vs Dezavantajları

### ✅ Avantajları

| Avantaj | Açıklama |
|---------|----------|
| **Deployment Olmaksızın Update** | Config değişikliği için uygulama restart gerekmez |
| **Merkezi Yönetim** | Tüm servislerin konfigürasyonu tek yerden |
| **Ortam Ayrımı** | dev/staging/prod net ve yönetilebilir |
| **Hızlı Güncelleme** | Seconds içinde etkili olur |
| **Feature Flag'ler** | A/B testing ve gradual rollout |
| **Version Control** | Git'te konfigürasyon history'si |
| **Rollback** | Eski konfigürasyonlara hızlı dönüş |

### ⚠️ Dikkat Edilecekler

| Nokta | Çözüm |
|-------|-------|
| Network Latency | Refresh'ten hemen sonra değişkende gecikme olabilir |
| Partial Updates | Bazı property'ler değişmeyebilir (@RefreshScope scope dışı) |
| Production Risk | Dikkatli test et ve plan yap |
| Kompleks Config Bağımlılıkları | Bean initialization sırasında değişenler güncellenmez |

---

## Önemli Endpoint'ler

| Endpoint | Metod | Açıklama |
|----------|-------|----------|
| `/actuator/refresh` | POST | Config değişikliklerini tetikle |
| `/actuator/configprops` | GET | @ConfigurationProperties bean'lerini göster |
| `/actuator/env` | GET | Ortam değişkenleri ve konfigürasyonlar |
| `/actuator/health` | GET | Uygulama sağlığı |
| `/api/v1/config/info` | GET | Uygulamaya özel aktif config bilgileri |
| `/api/v1/config/status` | GET | Config durumu ve versiyon |

---

## Çıktı Örneği

### Config Server Başlangıcı
```
2026-03-08 10:25:42 - Starting ConfigServerApplication
2026-03-08 10:25:45 - Tomcat started on port 8085
2026-03-08 10:25:45 - Config server started
2026-03-08 10:25:45 - Searching for config files in classpath:/order-service,classpath:/product-service,classpath:/saga-service
```

### Order Service Başlatılması
```
2026-03-08 10:26:00 - Starting OrderServiceApplication
2026-03-08 10:26:02 - Fetching config from http://localhost:8085
2026-03-08 10:26:02 - Loaded 15 properties from configserver
2026-03-08 10:26:05 - Tomcat started on port 5001
2026-03-08 10:26:05 - Order Service started [environment=dev]
```

### Refresh Tetiklemesi
```
2026-03-08 10:27:30 - POST /actuator/refresh received
2026-03-08 10:27:30 - Fetching config from http://localhost:8085 (refreshing)
2026-03-08 10:27:31 - DynamicConfigProperties bean recreated
2026-03-08 10:27:31 - 8 properties updated
2026-03-08 10:27:31 - Environment changed: dev → staging
2026-03-08 10:27:31 - Feature flags updated: enableNewOrderProcess false → true
```

---

## Referanslar

- **Config Server README:** `src/configserver/README.md`
- **Order Service README:** `src/orderservice/README.md`
- **DynamicConfigProperties Code:** `src/orderservice/src/main/java/.../DynamicConfigProperties.java`
- **ConfigController Code:** `src/orderservice/src/main/java/.../ConfigController.java`
- **Test Script:** `test-dynamic-config.ps1`

---

## Kaynaklar

- [Spring Cloud Config Server Docs](https://cloud.spring.io/spring-cloud-config/reference/html/)
- [Spring Cloud @RefreshScope](https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#refresh-scope)
- [@ConfigurationProperties](https://docs.spring.io/spring-boot/docs/current/reference/html/configuration-metadata.html#configuration-metadata-annotation-processor)
- [Actuator Endpoints](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)


