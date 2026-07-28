# Config Server

Bu modül, mikroservislerin konfigürasyonlarını merkezi olarak sunan **Spring Cloud Config Server** uygulamasıdır.

## Amaç

`config-server`, servislerin ortam bazlı ayarlarını (dev/prod gibi) tek noktadan yönetir.
Bu projede:

- Varsayılan çalışma profili `git`
- İhtiyaç halinde `native` profil desteği mevcut
- `order-service`, Config Server'dan ayar okuyacak şekilde entegre edilmiştir

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

`src/main/resources/` altında:

- `application.yml`  
  Ortak ayarlar (uygulama adı, port, aktif profil)
- `application-git.yml`  
  `git` profiline ait Config Server Git ayarları
- `application-native.yml`  
  `native` profiline ait classpath ayarları
- `order-service/`  
  `native` profil kullanılırsa örnek lokal config dosyaları

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
          search-locations: classpath:/order-service
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

### Varsayılan (git profili)

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\config-server"
.\mvnw.cmd spring-boot:run
```

### Native profili ile çalıştırma

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\config-server"
$env:SPRING_PROFILES_ACTIVE="native"
.\mvnw.cmd spring-boot:run
```

### Test

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\config-server"
.\mvnw.cmd test
```

## Config Server Uç Noktaları

Örnek sorgular:

- `GET http://localhost:8085/order-service/dev`
- `GET http://localhost:8085/order-service/prod`
- `GET http://localhost:8085/order-service/dev/master`

## Notlar

- Git profili için internet erişimi gerekir.
- Private Git repo kullanılırsa kimlik doğrulama ayarları (token/SSH) eklenmelidir.
- `application.properties` kaldırılmıştır; proje profil bazlı YML yapısı ile çalışır.

## Özet

`config-server`, bu mimaride merkezi konfigürasyon sunucusudur. Profil bazlı YML yaklaşımıyla (`application.yml`, `application-git.yml`, `application-native.yml`) hem Git tabanlı hem lokal (native) kullanım senaryoları desteklenir.
