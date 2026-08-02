package com.mertalptekin.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.security.Principal;

@SpringBootApplication
@EnableCaching // Spring Boot Cache yapısı aktif et
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

//    @Bean
//    @Primary
//    public KeyResolver ipKeyResolver() {
//        return exchange -> Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
//                .map(remoteAddress -> remoteAddress.getHostString())
//                .filter(host -> !host.isBlank())
//                .defaultIfEmpty("unknown");
//    }

//    @Bean
//    public KeyResolver userIdKeyResolver() {
//        return exchange -> exchange.getPrincipal()
//                .map(Principal::getName)
//                .filter(name -> !name.isBlank())
//                .defaultIfEmpty("anonymous");
//    }
}
