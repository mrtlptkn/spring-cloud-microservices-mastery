package com.mertalptekin.sagaservice.controller;



import com.mertalptekin.sagaservice.event.OrderSubmittedEvent;
import com.mertalptekin.sagaservice.service.OrderSagaService;
import org.springframework.web.bind.annotation.*;

// Başarılı Senaryo için -> Submitted -> StockReserved -> PaymentSucceeded -> Completed
// Başarısız Senaryo için -> Submitted -> StockNotAvailable -> Rejected
// {orderId,code,quantity,avaibleStock,status,message,timestamp, amount, balance}
// Event Streaming pattern Saga Orchestration Servicelerinde Eventlerin state takibi için kullanırız.

// Order Service -> Saga Service -> Inventory Service -> Saga Reply -> Payment Service -> Saga Reply -> Order Service
@RestController
@RequestMapping("/api/v1/saga")
public class SagaController {

    private final OrderSagaService orderSagaService;

    public SagaController(OrderSagaService orderSagaService) {
        this.orderSagaService = orderSagaService;
    }

    @PostMapping("submit")
    public void submitOrder(@RequestBody OrderSubmittedEvent orderSubmittedEvent) {

        this.orderSagaService.sendSubmitOrderEvent(orderSubmittedEvent);
    }

}
