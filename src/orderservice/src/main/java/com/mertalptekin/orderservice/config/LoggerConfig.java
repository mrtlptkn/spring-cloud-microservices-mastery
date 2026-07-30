package com.mertalptekin.orderservice.config;

import feign.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// feignLogger config işlemleri için -> tüm client ait akışı loglar.
// logging.level.com.mertalptekin.orderservice.client.ProductClient= DEBUG -> hangi client üzerinde DEBUG loglarının alınacağı kısmı
@Configuration
public class LoggerConfig {
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
}


