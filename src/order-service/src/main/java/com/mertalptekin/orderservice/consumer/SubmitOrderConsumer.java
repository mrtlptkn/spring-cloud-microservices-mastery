package com.mertalptekin.orderservice.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mertalptekin.orderservice.event.SubmitOrderFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@Slf4j
public class SubmitOrderConsumer {

    private final ObjectMapper objectMapper;

    public SubmitOrderConsumer(ObjectMapper objectMapper){
        this.objectMapper = objectMapper;
    }

    @Bean
    public Consumer<Message<String>> orderSubmitFailed(){
        return message -> {

            try {
               SubmitOrderFailedEvent event = objectMapper.readValue(message.getPayload(), SubmitOrderFailedEvent.class);

               // OrderDb üzrinden ilgili orderCode Find and Remove işlemleri yapılmalıdır.
                // Yada OrderEntity status -> OrderSubmitFailed olarak işaretlenip, reason -> kısmına ise event reason değeri set edilmelidir.
                log.info("OrderCode : " + event.orderCode() + " geri alma işlemi yapıldı");

            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

        };
    }

}
