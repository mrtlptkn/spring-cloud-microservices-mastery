package com.mertalptekin.orderservice.client;


import com.mertalptekin.orderservice.request.OrderedProductDetailRequest;
import com.mertalptekin.orderservice.response.OrderedProductDetailResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Component
@FeignClient("product-service") // openFeign Eureka Instance Name ile çalışır. Bu isim application-name
public interface ProductClient {

    // client oluştururken endpoint doğru olması request ve response nesnelerin eşleşmesi yeterlidir.
    // method ismi önemli değildir.
    @PostMapping("/api/v1/products/details")
    ResponseEntity<OrderedProductDetailResponse> getOrderedProducts(@RequestBody OrderedProductDetailRequest request);

}
