package com.mertalptekin.sagaservice.service;

import com.mertalptekin.sagaservice.event.*;
import com.mertalptekin.sagaservice.repository.IOrderSagaRepository;
import com.mertalptekin.sagaservice.entity.OrderSagaState;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Not: Eventlerin State değişimleri OrderSagaHandler üzerinden takip edilebilir.

@Service
public class OrderSagaHandler {

    private final OrderSagaService sagaService;
    private final IOrderSagaRepository orderSagaRepository;

    public OrderSagaHandler(OrderSagaService sagaService, IOrderSagaRepository orderSagaRepository) {
        this.sagaService = sagaService;
        this.orderSagaRepository = orderSagaRepository;
    }

    // Order Submitted Event alındığında stok kontrolü için CheckStockEvent fırlatılır.
    public void handleOrderSubmitted(OrderSubmittedEvent event) {
        var checkStockEvent = new CheckStockEvent(event.orderId(), event.code(), event.quantity());

        System.out.println("handleOrderSubmitted " + event);

        var sagaState = new OrderSagaState();
        sagaState.setStatus("Submitted");
        sagaState.setOrderId(event.orderId());
        sagaState.setCreatedAt(LocalDateTime.now());
        orderSagaRepository.save(sagaState);


        // State Transition State geçişi yaptığımız servis.
        this.sagaService.sendCheckStockEvent(checkStockEvent);
    }

    // Stok varsa step devam eder. Ödeme almamız lazım
    // Stok rezervasyonu başarılı ise ödeme işlemi için MakePaymentEvent fırlatılır.
    public void handleStockReserved(StockReservedEvent event) {

        Double amount = Math.random() * 100;

        var makePaymentEvent = new MakePaymentEvent(event.orderId(), event.code(),amount);
        System.out.println("handleStockReserved " + event);

        var sagaState = new OrderSagaState();
        sagaState.setStatus("StockReserved");
        sagaState.setOrderId(event.orderId());
        sagaState.setCreatedAt(LocalDateTime.now());
        orderSagaRepository.save(sagaState);

        this.sagaService.sendMakePaymentEvent(makePaymentEvent);
    }

    // Stock yok Step kesintiye uğrar
    // Stok rezervasyonu başarısız ise siparişi reddetmek için RejectOrderEvent fırlatılır.
    public void handleStockNotAvailable(StockNotAvailableEvent event) {

        System.out.println("handleStockNotAvailable " + event);
        var rejectEvent = new RejectOrderEvent(event.orderId(),"Stock not available");

        var sagaState = new OrderSagaState();
        sagaState.setStatus("StockNotAvailable");
        sagaState.setOrderId(event.orderId());
        sagaState.setCreatedAt(LocalDateTime.now());
        orderSagaRepository.save(sagaState);

        this.sagaService.sendRejectOrderEvent(rejectEvent);



    }

    // 3. Adım sonrasında ödeme alabiliyorsak buradan süreci sonlandırıyoruz.
    // Sagaa order servise git veritabanında status Completed yap.
    // Ödeme başarılı ise siparişi tamamlamak için CompleteOrderEvent fırlatılır.
    public void handlePaymentSucceeded(PaidSucceededEvent event) {
        var completeEvent = new CompleteOrderEvent(event.orderId());

        System.out.println("handlePaymentSucceeded " + completeEvent);

        var sagaState = new OrderSagaState();
        sagaState.setStatus("PaymentSucceeded");
        sagaState.setOrderId(event.orderId());
        sagaState.setReason(event.message());
        sagaState.setCreatedAt(LocalDateTime.now());
        orderSagaRepository.save(sagaState);

        this.sagaService.sendCompleteOrderEvent(completeEvent);
    }

    // 3. Adım için ödeme alamazsak bu bu durumda stok rezerve etmiş olabilir.
    // Rezerve edilen stokları geri alma prosedürü uygula, -> Inventory Service bunu ilet
    // Order Service git status=orderRejected olarak işaretle diyoruz.
    // Ödeme başarısız ise stok rezervasyonunu geri almak için ReleaseStockEvent ve siparişi reddetmek için RejectOrderEvent fırlatılır.
    public void handlePaymentFailed(PaidFailedEvent event) {

        System.out.println("handlePaymentFailed " + event);

        var sagaState = new OrderSagaState();
        sagaState.setStatus("PaymentFailed");
        sagaState.setOrderId(event.orderId());
        sagaState.setCreatedAt(LocalDateTime.now());
        sagaState.setReason(event.message());

        orderSagaRepository.save(sagaState);

        var releaseEvent = new ReleaseStockEvent(event.code(),event.orderId());
        this.sagaService.sendReleaseStockEvent(releaseEvent);

        var rejectEvent = new RejectOrderEvent(event.orderId(),"Payment failed");
        this.sagaService.sendRejectOrderEvent(rejectEvent);
    }
}

// Bu yöntem ile Order Sürecini AppendOnly T1 to T10 anını kayıt altına almış oluruz.
// Boşta kalan stepleri takip etmek ve hangi order için hangi işlem geçmişi olduğunu göremek açısında önemli
// Bu yönteme event streaming diyorum.
