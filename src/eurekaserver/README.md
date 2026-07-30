# Eureka Server

Bu modül, mikroservislerin birbirini bulabilmesi için kullanılan **Spring Cloud Netflix Eureka Server** uygulamasıdır.

## Amaç

`eureka-server`, sistemdeki servislerin kayıt olup birbirini dinamik olarak keşfedebildiği merkezi servis keşif sunucusudur.
Bu projede:

- `@EnableEurekaServer` ile Eureka Server aktif edilmiştir
- Servis kayıt/keşif işlemleri için tek merkez olarak çalışır
- Kendisi bir server olduğu için registry'e client gibi kayıt olmaz

## Kullanılan Sürümler

`pom.xml` dosyasına göre:

- **Java:** 17
- **Spring Boot:** 4.0.1
- **Spring Cloud:** 2025.1.0
- **Build Tool:** Maven

## Kullanılan Teknolojiler

- **Spring Boot**
- **Spring Cloud Netflix Eureka Server**
- **Spring Web MVC**
- **Spring Boot Test / JUnit 5**
- **Maven Wrapper** (`mvnw`, `mvnw.cmd`)

## Konfigürasyon Dosya Yapısı

`src/main/resources/` altında:

- `application.yml`
  Eureka Server uygulama adı, port ve client davranışları

## Uygulama Konfigürasyonu

### Ortak ayarlar (`application.yml`)

```yaml
server:
  port: 8761

spring:
  application:
    name: eureka-server

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
```

- Uygulama adı: `eureka-server`
- Port: `8761`
- `register-with-eureka: false`: sunucu kendini Eureka'ya kaydetmez
- `fetch-registry: false`: sunucu registry'i client gibi çekmez

## Bağımlılıklar

Ana bağımlılıklar:

- `org.springframework.cloud:spring-cloud-starter-netflix-eureka-server`
- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-webmvc-test` (test)

## Diğer Servislerle İlişki

- Diğer mikroservisler (`order-service`, `product-service`, `gateway` vb.) bu sunucuya kayıt olur.
- Servisler birbirine erişmeden önce Eureka üzerinden hedef servis instance bilgisini alır.

## Çalıştırma

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\eureka-server"
.\mvnw.cmd spring-boot:run
```

## Test

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\eureka-server"
.\mvnw.cmd test
```

## Eureka Dashboard

Uygulama ayağa kalktıktan sonra:

- `http://localhost:8761`

## Notlar

- `application.properties` kaldırıldı; proje `application.yml` ile çalışacak şekilde düzenlendi.
- Eureka'ya kaydolacak her serviste uygun `eureka.client.service-url.defaultZone` tanımı olmalıdır.

### Production için önerilen ayarlar

- Dashboard ve servis kayıt endpoint'lerini dış dünyaya açacaksanız kimlik doğrulama ekleyin (Spring Security + network kısıtlama). **Neden:** Bu endpoint'ler servis topolojisini açığa çıkarır. **Etki:** Yetkisiz erişim ve bilgi sızması riski azalır.
- `server.forward-headers-strategy` ve ters proxy (Nginx/Ingress) ayarlarını doğru yapın; üretilen URL'lerin `https` olmasını garanti edin. **Neden:** Proxy arkasında yanlış header işlemesi hatalı yönlendirme ve mixed-content sorunları üretir. **Etki:** Doğru callback/link üretimi ve güvenli trafik sağlanır.
- Tek nokta hatasını azaltmak için en az 2-3 Eureka node ile cluster kurun; node'lar arasında `eureka.client.register-with-eureka=true` ve `fetch-registry=true` kullanın. **Neden:** Tek node düşerse servis keşfi durabilir. **Etki:** Servis discovery yüksek erişilebilirlik kazanır.
- Her servis için `eureka.instance.prefer-ip-address`, `lease-renewal-interval-in-seconds`, `lease-expiration-duration-in-seconds` değerlerini ortama göre optimize edin. **Neden:** Varsayılan lease değerleri her trafik profiline uymaz. **Etki:** Gereksiz stale kayıtlar azalır, node kayıpları daha hızlı algılanır.
- `management.endpoints.web.exposure.include=health,info,prometheus` gibi actuator ayarlarıyla health gözlemi ve metrik toplama yapın. **Neden:** Arıza belirtilerini erken görmek için ölçülebilirlik gerekir. **Etki:** Proaktif izleme ve daha hızlı incident müdahalesi sağlanır.
- Log toplama ve izleme (ELK/Prometheus/Grafana) ekleyin; dashboard'da instance sayısı ve heartbeat kayıplarını alarma bağlayın. **Neden:** Dağıtık yapıda manuel takip geç ve eksik kalır. **Etki:** Otomatik alarm ile kesinti süresi ve MTTR düşürülür.

## Özet

`eureka-server`, mikroservis mimarisinde servis keşfi katmanını sağlar. Servis instance'larını merkezi olarak tutar ve sistemdeki servisler arası dinamik iletişimi kolaylaştırır.
