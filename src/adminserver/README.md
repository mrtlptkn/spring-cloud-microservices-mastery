# Admin Server

Bu modül, mikroservis ekosistemindeki servislerin sağlık durumu, metrikleri, log seviyeleri ve `Actuator` uç noktalarını merkezi olarak izlemek için kullanılan bir **Spring Boot Admin Server** uygulamasıdır.

## Amaç

`admin-server`, sistemde çalışan Spring Boot tabanlı servisleri tek bir panel üzerinden gözlemlemeyi sağlar. Bu proje özelinde özellikle aşağıdaki servislerin Admin Server'a bağlanması hedeflenmiştir:

- `order-service`
- `product-service`

Bu iki servis içinde `spring-boot-admin-starter-client` bağımlılığı ve Admin Server adresi tanımlanmıştır.

## Kullanılan Sürümler

Projede kullanılan temel sürümler `pom.xml` dosyasına göre aşağıdaki gibidir:

- **Java:** 17
- **Spring Boot:** 3.5.9
- **Spring Boot Admin:** 3.5.6
- **Build Tool:** Maven

## Kullanılan Teknolojiler

Bu modülde kullanılan başlıca teknolojiler:

- **Spring Boot**
- **Spring Boot Admin Server**
- **Spring Web**
- **Spring Boot Test / JUnit 5**
- **Maven Wrapper** (`mvnw`, `mvnw.cmd`)

## Proje Yapısı

Öne çıkan dosyalar:

- `pom.xml`  
  Projenin bağımlılıklarını, Java sürümünü ve plugin yapılandırmalarını içerir.
- `src/main/java/com/mertalptekin/adminserver/AdminServerApplication.java`  
  Uygulamanın başlangıç sınıfıdır. `@EnableAdminServer` anotasyonu ile Admin Server özelliği aktif edilir.
- `src/main/resources/application.properties`  
  Uygulama adı ve port bilgisi burada tanımlıdır.

## Uygulama Konfigürasyonu

`application.properties` içeriğine göre:

- Uygulama adı: `admin-server`
- Çalışma portu: `8081`

Yani uygulama ayağa kalktığında varsayılan olarak şu adreste erişilebilir:

- `http://localhost:8081`

## Bağımlılıklar

`pom.xml` dosyasına göre bu modülün doğrudan kullandığı ana bağımlılıklar şunlardır:

### 1. `de.codecentric:spring-boot-admin-starter-server`
Spring Boot Admin sunucusunu sağlar. Admin arayüzü, servis kaydı, izleme ve yönetim yetenekleri bu bağımlılık üzerinden gelir.

### 2. `org.springframework.boot:spring-boot-starter-web`
Uygulamanın web tabanlı olarak çalışmasını sağlar. Admin panelinin HTTP üzerinden sunulması için gereklidir.

### 3. `org.springframework.boot:spring-boot-starter-test`
Test altyapısı için kullanılır. `JUnit 5` ve Spring test bileşenlerini içerir.

## Çalıştırmak İçin Gerekli Ön Koşullar

Bu modülü çalıştırmak için minimum gereksinimler:

- **JDK 17**
- **İnternet erişimi**  
  İlk derleme sırasında Maven bağımlılıklarının indirilmesi için gerekir.
- **Maven**  
  İsteğe bağlıdır. Çünkü proje içinde `mvnw.cmd` bulunduğu için ayrıca Maven kurmadan da çalıştırabilirsiniz.

## Bu Servisin Diğer Projelerle İlişkisi

`admin-server` modülü, diğer servisleri işlevsel olarak yönetmez; onları **izler**.

### Doğrudan kod bağımlılığı var mı?
Hayır. Bu proje içinde `order-service`, `product-service`, `gateway`, `config-server` veya `eureka-server` için doğrudan bir derleme bağımlılığı bulunmamaktadır.

### İşlevsel bağımlılık / entegrasyon ilişkisi var mı?
Evet. Anlamlı şekilde kullanılabilmesi için izlenecek servislerin Admin Server'a istemci olarak bağlanması gerekir.

Bu repo içinde şu servislerde Admin Client yapılandırması bulunmaktadır:

- `order-service`
- `product-service`

Bu servislerde aşağıdaki türde ayarlar yer almaktadır:

- `spring.boot.admin.client.enabled=true`
- `spring.boot.admin.client.url=http://localhost:8081`
- `management.endpoints.web.exposure.include=*`

Bu sayede ilgili servisler çalıştığında kendilerini `admin-server` uygulamasına kaydedebilir.

## İzleme İçin Gerekli Uygulama Tarafı Şartları

Bir Spring Boot servisinin Admin Server ekranında görünmesi için genellikle aşağıdakiler gerekir:

1. Serviste `spring-boot-admin-starter-client` bağımlılığı olmalı.
2. Servisin `Actuator` uç noktaları açık olmalı.
3. Servis içinde Admin Server adresi tanımlanmış olmalı.
4. Admin Server çalışıyor olmalı.

Bu projede `order-service` ve `product-service` tarafında bu entegrasyonun yapıldığı görülmektedir.

## Nasıl Çalıştırılır?

### Windows için Maven Wrapper ile

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\admin-server"
.\mvnw.cmd spring-boot:run
```

### Alternatif olarak jar üretip çalıştırma

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\admin-server"
.\mvnw.cmd clean package
java -jar .\target\admin-server-0.0.1-SNAPSHOT.jar
```

## Test Çalıştırma

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\admin-server"
.\mvnw.cmd test
```

## Beklenen Çalışma Senaryosu

Önerilen sıra:

1. `admin-server` uygulamasını başlatın.
2. `order-service` ve `product-service` uygulamalarını başlatın.
3. Servisler Admin Server'a kayıt olduğunda arayüzde görünmelerini kontrol edin.
4. Tarayıcıdan `http://localhost:8081` adresine gidin.

## Notlar

- Bu modül tek başına çalışabilir; ancak panelin anlamlı veri göstermesi için istemci servislerin de çalışıyor olması gerekir.
- `admin-server` içinde şu anda güvenlik yapılandırması bulunmamaktadır. Yani varsayılan kullanım geliştirme ortamı odaklıdır.
- Bu modülde veritabanı, Kafka, Redis veya Keycloak gibi ek bir zorunlu altyapı bağımlılığı görünmemektedir.
- `config-server` veya `eureka-server` ile doğrudan entegrasyon yapılandırması bu modülde bulunmamaktadır.

## Özet

`admin-server`, bu mikroservis mimarisinde merkezi gözlemleme paneli olarak görev yapar. Teknik olarak sade bir modüldür; esas amacı diğer Spring Boot servislerini tek ekrandan izlemektir. Bu repo içinde özellikle `order-service` ve `product-service` ile birlikte çalışacak şekilde konumlandırılmıştır.

