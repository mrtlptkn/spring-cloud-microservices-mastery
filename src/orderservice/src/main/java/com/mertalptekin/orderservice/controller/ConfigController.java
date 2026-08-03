package com.mertalptekin.orderservice.controller;

import com.mertalptekin.orderservice.config.DynamicConfigProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dinamik konfigürasyon bilgilerini sunmak için Controller.
 * Bu controller @RefreshScope sayesinde config değişikliklerini
 * anında yansıtır.
 */
@RestController
@RequestMapping("api/v1/config")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Configuration", description = "Dinamik konfigürasyon bilgileri")
public class ConfigController {

    private final DynamicConfigProperties dynamicConfigProperties;

    /**
     * Aktif olan tüm konfigürasyonları gösterir.
     * /actuator/refresh çağrıldıktan sonra yeni değerler döner.
     */
    @GetMapping("info")
    @Operation(summary = "Aktif konfigürasyon bilgileri",
               description = "Şu anki aktif olan tüm konfigürasyon değerlerini döner")
    public ResponseEntity<Map<String, Object>> getConfigInfo() {
        Map<String, Object> configInfo = new LinkedHashMap<>();

        configInfo.put("environment", dynamicConfigProperties.getEnvironment());
        configInfo.put("version", dynamicConfigProperties.getVersion());

        // Database bilgileri
        Map<String, Object> dbConfig = new LinkedHashMap<>();
        dbConfig.put("host", dynamicConfigProperties.getDatabase().getHost());
        dbConfig.put("port", dynamicConfigProperties.getDatabase().getPort());
        dbConfig.put("name", dynamicConfigProperties.getDatabase().getName());
        dbConfig.put("username", dynamicConfigProperties.getDatabase().getUsername());
        dbConfig.put("jdbcUrl", dynamicConfigProperties.getDatabase().getJdbcUrl());
        configInfo.put("database", dbConfig);

        // API Keys bilgileri
        Map<String, Object> apiConfig = new LinkedHashMap<>();
        apiConfig.put("productServiceUrl", dynamicConfigProperties.getApiKeys().getProductServiceUrl());
        apiConfig.put("notificationServiceUrl", dynamicConfigProperties.getApiKeys().getNotificationServiceUrl());
        apiConfig.put("requestTimeoutMs", dynamicConfigProperties.getApiKeys().getRequestTimeoutMs());
        configInfo.put("apiKeys", apiConfig);

        // Feature Flags bilgileri
        Map<String, Object> featureConfig = new LinkedHashMap<>();
        featureConfig.put("enableNewOrderProcess", dynamicConfigProperties.getFeatureFlags().getEnableNewOrderProcess());
        featureConfig.put("enableNotificationService", dynamicConfigProperties.getFeatureFlags().getEnableNotificationService());
        featureConfig.put("enableDetailedLogging", dynamicConfigProperties.getFeatureFlags().getEnableDetailedLogging());
        featureConfig.put("maxOrderRetry", dynamicConfigProperties.getFeatureFlags().getMaxOrderRetry());
        configInfo.put("featureFlags", featureConfig);

        log.info("Aktif konfigürasyon bilgileri istendi. Ortam: {}", dynamicConfigProperties.getEnvironment());
        return ResponseEntity.ok(configInfo);
    }

    /**
     * Konfigürasyonun güncelleme tarihini/bilgisini gösterir.
     */
    @GetMapping("status")
    @Operation(summary = "Konfigürasyon durumu",
               description = "Hangi ortamda çalıştığını ve versiyon bilgisini döner")
    public ResponseEntity<Map<String, String>> getConfigStatus() {
        return ResponseEntity.ok(Map.of(
            "environment", dynamicConfigProperties.getEnvironment(),
            "version", dynamicConfigProperties.getVersion(),
            "message", "Config değişikliklerini uygulamak için /actuator/refresh endpoint'ini POST ile çağırın"
        ));
    }
}

