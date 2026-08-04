package com.mertalptekin.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("fallback")
@Tag(name = "Fallback", description = "Gateway fallback endpoint'leri")
public class FallbackController {

    // Circuit Breaker tetiklendiğinde ve Gateway isteği forward (yönlendirme) yaptığında, orijinal isteğin HTTP Metodunu (GET, POST, PUT, DELETE) korur.
    // Eğer servise bir POST veya PUT isteği attıysanız, ancak Fallback Controller'ınızda sadece @GetMapping tanımlıysa, Spring WebFlux "Ben bu URL için POST isteği kabul etmiyorum" diyerek 405 Method Not Allowed hatası fırlatır.
    @RequestMapping("product-service")
    @Operation(summary = "Product Service fallback", description = "Circuit breaker acik oldugunda product-service icin varsayilan fallback yaniti doner")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fallback yaniti basariyla dondu")
    })
    public Mono<String> productServiceFallback() {
        return Mono.just("The Product Service is currently unavailable. Please try again later.");
    }
}
