package com.mertalptekin.productservice.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mertalptekin.productservice.event.SubmitOrderEvent;
import com.mertalptekin.productservice.event.SubmitOrderFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@Slf4j
public class SubmitOrderConsumer {

    private final ObjectMapper objectMapper;
    private final StreamBridge streamBridge;

    public SubmitOrderConsumer(ObjectMapper objectMapper, StreamBridge streamBridge){
        this.objectMapper = objectMapper;
        this.streamBridge = streamBridge;
    }

    // consumer mesaj hangi binding üzerinden bana hangi topic den mesaj gelicek
    // ve ben bu mesajı hangi methoda gönderip, eventin tetiklenmesini sağlayacağım.
    // Consumer ederken gördüğü herhangi bir exception DLQ gönderir.
    @Bean
    public Consumer<Message<String>> orderSubmitted(){
        return message -> {

            try {
                SubmitOrderEvent data = objectMapper.readValue(message.getPayload(), SubmitOrderEvent.class);

                // Not: Eğer unchecked exception veya checked exception durumlarında gönderilen mesajın hatalı mesaj kuyruğuna atılmasını istiyorsal dlq -> dead letter queue özelliğini aktif hale getirmemiz lazım

                if(data.items().stream().filter(x-> x.getStock() >=10).count()>0){
                    // Mesajı consume ederken exception fırlattığımızda bu mesaj dıoğru iletilmiş ama tüketilememiş bir mesaj olmuş oluyor. Yani bu mesaj için deadletter queue taşımamız lazım.
                    log.info("Bir siparişte en fazla 5 adet ürün sipariş edilebilir");

                    var failedEvent = new SubmitOrderFailedEvent(data.orderCode(),"Bir siparişte en fazla 5 adet ürün sipariş edilebilir");
                    String payload = objectMapper.writeValueAsString(failedEvent);

                    // Compensate ile Throw DLQ aynı anda işlenirse mantık hatası meydan gelir. İkisinden birinin işlenmesi gerekir.
                    streamBridge.send("orderSubmitFailed-out-0",payload);

                    // throw new RuntimeException("Bir siparişte en fazla 5 adet ürün sipariş edilebilir");
                }


                log.info("Subcribed message " + data.orderCode());

            } catch (JsonProcessingException e) {
                log.info("JsonProcessingException " + e.getMessage());
                throw new RuntimeException(e);
            }

        };
    }

}
