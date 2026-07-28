package com.mertalptekin.gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("fallback")
public class FallbackController {

    // Circuit Breaker tetiklendiğinde ve Gateway isteği forward (yönlendirme) yaptığında, orijinal isteğin HTTP Metodunu (GET, POST, PUT, DELETE) korur.
    // Eğer servise bir POST veya PUT isteği attıysanız, ancak Fallback Controller'ınızda sadece @GetMapping tanımlıysa, Spring WebFlux "Ben bu URL için POST isteği kabul etmiyorum" diyerek 405 Method Not Allowed hatası fırlatır.
    @PostMapping("product-service")
    public Mono<String> productServiceFallback() {
        return Mono.just("The Product Service is currently unavailable. Please try again later.");
    }
}
