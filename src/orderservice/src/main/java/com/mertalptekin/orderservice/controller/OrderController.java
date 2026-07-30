package com.mertalptekin.orderservice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mertalptekin.orderservice.client.ProductClient;
import com.mertalptekin.orderservice.event.SubmitOrderEvent;
import com.mertalptekin.orderservice.request.OrderedProductDetailRequest;
import com.mertalptekin.orderservice.response.OrderedProductDetailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/orders")
@Slf4j
public class OrderController {
    private final ProductClient productClient;
    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;

    public OrderController(ProductClient productClient, StreamBridge streamBridge, ObjectMapper objectMapper){
        this.productClient = productClient;
        this.streamBridge = streamBridge;
        this.objectMapper = objectMapper;
    }
    // api/v1/orders/123/orderDetails
    @GetMapping("{orderCode}/orderDetails")
    public ResponseEntity<OrderedProductDetailResponse> getOrderedProductDetails(String orderCode){
        String[] productIds = new String[1];
        productIds[0] = UUID.randomUUID().toString();
        //productIds[1] = UUID.randomUUID().toString(); // veri tabanında çekilmiş gibi simüle ettik
        log.info("order-service-request");
        return productClient.getOrderedProducts(new OrderedProductDetailRequest(productIds));
    }


    @PostMapping("sendKafka")
    public ResponseEntity<String> sendMessageToKafka(@RequestBody SubmitOrderEvent request) throws JsonProcessingException {

       String payload = objectMapper.writeValueAsString(request); // payload karşı microservice gönderilecek olan jsonString data

        Message<String> message = MessageBuilder.withPayload(payload).build();
        boolean isSend = streamBridge.send("submitOrder-out-0",message);

        return ResponseEntity.ok(isSend ? "Mesaj teslim edildi":"Mesaj iletilemedi");
    }

    // find Ordered Product In DB
    // var response = orderRepository.findByOrderCode(orderCode)
    // response.productIds -> sipariş edilen ürünlerimiz


}
