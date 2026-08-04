# Gateway Circuit Breaker Profili

Bu doküman, `gateway` modülüne eklenen `application-circuitbreaker.yml` dosyasının ne yaptığını ve nasıl test edileceğini anlatır.

## Amaç

Bu profil ile `product-service` çağrıları için gateway seviyesinde bir **Resilience4j Circuit Breaker** tanımlanır.

Aktif olduğunda:

- `product-service` tarafında art arda hatalar oluşursa devre açılır.
- Devre açıkken istekler doğrudan fallback endpoint'ine yönlendirilir.
- `waitDurationInOpenState` süresi dolunca devre `HALF_OPEN` durumuna geçer.
- Başarılı deneme çağrıları gelirse devre tekrar kapanır.

## Eklenen profil dosyası

Dosya:

- `src/main/resources/application-circuitbreaker.yml`

Profil adı:

- `circuitbreaker`

Bu profil aşağıdaki parçaları içerir:

1. `product-service` route'u üzerinde `CircuitBreaker` filter tanımı
2. `productServiceBreaker` isimli Resilience4j circuit breaker ayarları
3. `productServiceBreaker` için time limiter ayarları

## Kullanılan ayarlar

Özet davranış:

- Son **3** çağrı izlenir.
- Karar vermek için en az **3** çağrı gerekir.
- Hata oranı **%50** ve üzeri olduğunda devre açılır.
- Devre açık kaldıktan **30 saniye** sonra `HALF_OPEN` durumuna geçer.
- `HALF_OPEN` durumunda **2** deneme çağrısına izin verilir.
- Timeout süresi **20 saniye** olarak ayarlanmıştır.
- `500`, `503` ve `504` HTTP durum kodları hata olarak sayılır.
- `Throwable`, `TimeoutException` ve `IOException` tipleri kayıt altına alınır.

## Fallback endpoint

Gateway içinde kullanılan fallback endpoint:

- `/fallback/product-service`

Bu endpoint artık yalnızca `POST` değil, farklı HTTP metodları ile de cevap verebilir. Bu sayede hem manuel test hem de olası route senaryoları daha sorunsuz ilerler.

Fallback yanıtı:

```text
The Product Service is currently unavailable. Please try again later.
```

## Profili nasıl çalıştırırım?

`circuitbreaker` profili tek başına güvenlik modu belirlemez. Bu nedenle genelde bir güvenlik profili ile birlikte çalıştırılmalıdır.

### Lokal ve kolay test için

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\gateway"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=public,circuitbreaker"
```

### Keycloak ile birlikte çalıştırmak için

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\gateway"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=keycloak,circuitbreaker"
```

## Test ön koşulları

En azından aşağıdaki servislerin hazır olması gerekir:

1. `eurekaserver`
2. `gateway`
3. `productservice`

`product-service`, Eureka'ya kayıtlı olmalıdır. Aksi halde gateway `lb://product-service` hedefini çözemeyecektir.

## Test senaryosu 1: Normal akış

Önce `productservice` çalışırken gateway üzerinden başarılı cevap alındığını doğrulayın.

İstek:

```powershell
$body = '{"productIds":["P-1"]}'
Invoke-RestMethod -Method Post -Uri "http://localhost:8084/product-service/api/v1/products/details" -ContentType "application/json" -Body $body
```

Beklenen sonuç:

- `200 OK`
- `product-service` gerçek cevabı döner
- fallback devreye girmez

## Test senaryosu 2: 500 hatası ile circuit breaker açılması

`productservice` içinde `productIds.length == 2` olduğunda `RuntimeException` fırlatılıyor. Bu davranış test için kullanılabilir.

Hata üreten örnek istek:

```powershell
$body = '{"productIds":["P-1","P-2"]}'
Invoke-WebRequest -Method Post -Uri "http://localhost:8084/product-service/api/v1/products/details" -ContentType "application/json" -Body $body
```

Bu isteği en az **3 kez** gönderin. Çünkü:

- `slidingWindowSize: 3`
- `minimumNumberOfCalls: 3`

Üç başarısız çağrıdan sonra devre açılmalıdır.

Pratik test döngüsü:

```powershell
$body = '{"productIds":["P-1","P-2"]}'
1..4 | ForEach-Object {
    try {
        Invoke-WebRequest -Method Post -Uri "http://localhost:8084/product-service/api/v1/products/details" -ContentType "application/json" -Body $body
    }
    catch {
        $_.Exception.Response.StatusCode.value__
    }
}
```

Beklenen davranış:

- İlk çağrılar `500` olabilir.
- Devre açıldıktan sonraki çağrılar fallback'e düşer.
- Fallback cevabı `200 OK` ve sabit mesaj olur.

## Test senaryosu 3: Fallback doğrulaması

Devre açıldıktan sonra aynı isteği tekrar gönderin.

Beklenen sonuç:

```text
The Product Service is currently unavailable. Please try again later.
```

İsterseniz fallback endpoint'ini doğrudan da test edebilirsiniz:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8084/fallback/product-service"
```

## Test senaryosu 4: Open -> Half Open -> Closed geçişi

1. Devre açıldıktan sonra **30 saniye** bekleyin.
2. `productservice` ayakta ve sağlıklı durumda olsun.
3. Başarılı istek gönderin.

Örnek:

```powershell
Start-Sleep -Seconds 30
$body = '{"productIds":["P-1"]}'
Invoke-RestMethod -Method Post -Uri "http://localhost:8084/product-service/api/v1/products/details" -ContentType "application/json" -Body $body
```

Beklenen davranış:

- Devre `HALF_OPEN` durumunda deneme çağrılarını kabul eder.
- Başarılı sonuçlar gelirse devre tekrar `CLOSED` olur.

## Actuator ile kontrol

Circuit breaker durumunu aşağıdaki endpoint'lerden izleyebilirsiniz:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8084/actuator/circuitbreakers"
Invoke-RestMethod -Method Get -Uri "http://localhost:8084/actuator/health"
```

Gerekirse daha ayrıntılı sağlık çıktısı için:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8084/actuator/health" | ConvertTo-Json -Depth 10
```

## Önemli notlar

- `circuitbreaker` ve `ratelimiter` profilleri aynı anda açılırsa route listeleri birbirini ezebilir. Bu nedenle ayrı ayrı test etmek daha güvenlidir.
- Timeout senaryosu bu profilde `20s` olduğu için, `productservice` içindeki `3 saniye` gecikme timeout üretmez.
- Timeout davranışını özellikle test etmek isterseniz, geçici olarak daha düşük bir `timeoutDuration` ile ayrı bir test profili kullanabilirsiniz.

## Otomatik testler

Bu değişiklik ile birlikte `gateway` testleri şunları doğrular:

- `circuitbreaker` profilinin yüklenmesi
- `productClient` route'u üzerinde `CircuitBreaker` filter tanımı
- fallback endpoint'inin cevap vermesi

Çalıştırmak için:

```powershell
cd "C:\Users\merta\Desktop\Spring Cloud Microservices\spring-cloud-microservices-mastery\src\gateway"
.\mvnw.cmd test
```
