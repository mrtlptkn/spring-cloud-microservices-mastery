package com.mertalptekin.orderservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * Dinamik olarak yüklenebilecek konfigürasyon özellikleri.
 * @RefreshScope sayesinde /actuator/refresh endpoint'i çağrıldığında
 * değerler yeniden Config Server'dan okunur ve güncelleştiril.
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "app.order-service")
public class DynamicConfigProperties {

    /**
     * Veritabanı bağlantı bilgileri
     */
    @Data
    public static class Database {
        private String host = "localhost";
        private Integer port = 5432;
        private String name = "orderdb";
        private String username = "orderuser";
        private String password = "orderpass";

        public String getJdbcUrl() {
            return String.format("jdbc:postgresql://%s:%d/%s", host, port, name);
        }
    }

    /**
     * API anahtarları ve harici servis bağlantıları
     */
    @Data
    public static class ApiKeys {
        private String productServiceUrl = "http://product-service:8080";
        private String notificationServiceUrl = "http://notification-service:8080";
        private String apiKeyExternal = "";
        private Integer requestTimeoutMs = 5000;
    }

    /**
     * Özellik Flag'leri (Feature Flags)
     */
    @Data
    public static class FeatureFlags {
        private Boolean enableNewOrderProcess = false;
        private Boolean enableNotificationService = true;
        private Boolean enableDetailedLogging = false;
        private Integer maxOrderRetry = 3;
    }

    private Database database = new Database();
    private ApiKeys apiKeys = new ApiKeys();
    private FeatureFlags featureFlags = new FeatureFlags();

    /**
     * Ortam bilgisi (dev, staging, prod)
     */
    private String environment = "dev";

    /**
     * Servis açıklaması
     */
    private String version = "1.0.0";
}

