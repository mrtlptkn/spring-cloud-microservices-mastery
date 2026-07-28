package com.mertalptekin.sagaservice.service;


import com.mertalptekin.sagaservice.event.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

// Reply Channel
@Service
    public class OrderSagaConsumer {

        private final OrderSagaHandler sagaHandler;


        public OrderSagaConsumer(OrderSagaHandler sagaHandler) {
            this.sagaHandler = sagaHandler;
        }

        // 1. Adıma ait Consumer
        @Bean
        public Consumer<OrderSubmittedEvent> orderSubmitEvent() {
            return sagaHandler::handleOrderSubmitted;
        }

        // 2. Adıma ait Consumer
        // Inventory Service Consumer
        @Bean
        public Consumer<CheckStockEvent> checkStockEvent() {

            return  event -> {

                long availableStock = Math.round(Math.random() * 100);

                System.out.println("Ürün Stoğu" + availableStock);
                System.out.println("Sipariş Adeti " + event.quantity());

                if(event.quantity() > availableStock) {
                    sagaHandler.handleStockNotAvailable(new StockNotAvailableEvent(event.orderId(),event.code()));

                } else {
                    sagaHandler.handleStockReserved(new StockReservedEvent(event.orderId(),event.code(),event.quantity()));
                }
            };
        }

        // Payment Service Consumer
        // 3. Adım olan ödeme adımı gönderildiğinde, tüketilecek olan consumer
        @Bean
        public  Consumer<MakePaymentEvent> makePaymentEvent() {
            return event -> {
                double balance = Math.random() * 100; // bakiye
                System.out.println("bakiye :" + balance);
                System.out.println("ödenecek tutar :" + event.amount());
                if(event.amount() > balance){ // ödenecek tutar bakiyeden fazla ise limit yetersiz
                    sagaHandler.handlePaymentFailed(new PaidFailedEvent(event.orderId(),event.code(),"Bakiye Yetersiz"));
                } else {
                    sagaHandler.handlePaymentSucceeded(new PaidSucceededEvent(event.orderId(),"Ödeme alındı"));
                }
            };
        }

        // Eğer Inventory serviste stockReservedEvent dinlersem bu durumda nasıl bir step den devam eceğime karar verebilirim.
    // Not: Saga Servisler karar adımlarını uygularken replay channeldan dönen eventleri dinleyerek karar verebilir.
    // Saga Replay Channel Consumer
        @Bean
        public Consumer<StockReservedEvent> stockReservedEvent() {
            return sagaHandler::handleStockReserved;
        }

        @Bean
        public Consumer<StockNotAvailableEvent> stockNotAvailableEvent() {
            return sagaHandler::handleStockNotAvailable;
        }

        @Bean
        public Consumer<PaidSucceededEvent> paidSucceededEvent() {
            return sagaHandler::handlePaymentSucceeded;
        }

        @Bean
        public Consumer<PaidFailedEvent> paidFailedEvent() {
            return sagaHandler::handlePaymentFailed;
        }

        // Burada checkStockEvent-in-0 ve diğerlerine ihtiyaç olabilir.
    }


