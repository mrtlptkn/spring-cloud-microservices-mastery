package com.mertalptekin.sagaservice.controller;



import com.mertalptekin.sagaservice.event.OrderSubmittedEvent;
import com.mertalptekin.sagaservice.service.OrderSagaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

// Başarılı Senaryo için -> Submitted -> StockReserved -> PaymentSucceeded -> Completed
// Başarısız Senaryo için -> Submitted -> StockNotAvailable -> Rejected
// {orderId,code,quantity,avaibleStock,status,message,timestamp, amount, balance}
// Event Streaming pattern Saga Orchestration Servicelerinde Eventlerin state takibi için kullanırız.

// Order Service -> Saga Service -> Inventory Service -> Saga Reply -> Payment Service -> Saga Reply -> Order Service
@RestController
@RequestMapping("/api/v1/saga")
@Tag(name = "Saga", description = "Saga orchestration endpoint'leri")
public class SagaController {

    private final OrderSagaService orderSagaService;

    public SagaController(OrderSagaService orderSagaService) {
        this.orderSagaService = orderSagaService;
    }

    @PostMapping("submit")
    @Operation(summary = "Saga surecini baslat", description = "OrderSubmittedEvent alir ve saga_order_submitted topic'ine ilk eventi yollar")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Event basariyla yayinlandi", content = @Content),
            @ApiResponse(responseCode = "500", description = "Sunucu hatasi")
    })
    public void submitOrder(@RequestBody OrderSubmittedEvent orderSubmittedEvent) {

        this.orderSagaService.sendSubmitOrderEvent(orderSubmittedEvent);
    }

}
