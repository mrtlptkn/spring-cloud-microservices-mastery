package com.mertalptekin.productservice.controller;


import com.mertalptekin.productservice.dto.OrderedProduct;
import com.mertalptekin.productservice.request.OrderedProductDetailRequest;
import com.mertalptekin.productservice.response.OrderedProductDetailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("api/v1/products")
@Slf4j
public class ProductsController {

    private final String serverPort;

    public ProductsController(@Value("${server.port}") String serverPort){
        this.serverPort = serverPort;
    }


    // api/v1/products/details
    @PostMapping("details")
    public ResponseEntity<OrderedProductDetailResponse> productDetailRequest(@RequestBody OrderedProductDetailRequest request) throws InterruptedException {


        if(request.ProductIds().length == 2){
            throw  new RuntimeException("Sunucuda bir hata meydana geldi");
        }

        if(request.ProductIds().length == 3){
            Thread.sleep(3000);
        }

        // kendi veri tabanında requestProductIds göre sorgulanıp çekildi
        List<OrderedProduct> orderedProducts = Arrays.asList(new OrderedProduct("P-1", BigDecimal.valueOf(100.2),10),new OrderedProduct("P-2", BigDecimal.valueOf(100.2),10));

        log.info("product-request" + serverPort);

        // response olarak iletildi.
        return ResponseEntity.ok(new OrderedProductDetailResponse(orderedProducts));
    }


}
