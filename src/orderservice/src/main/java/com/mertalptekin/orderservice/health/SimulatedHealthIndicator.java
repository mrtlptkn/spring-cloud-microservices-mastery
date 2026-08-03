package com.mertalptekin.orderservice.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simülasyon amaçlı Health Indicator.
 * REST endpoint üzerinden instance'ı programatik olarak DOWN yapmanızı sağlar.
 * Böylece Eureka'nın UP'da gösterdiği ama gerçekte sağlıksız olan
 * instance senaryosunu test edebilirsiniz.
 *
 * Kullanım:
 *   POST /api/v1/admin/health/break  → Instance DOWN
 *   POST /api/v1/admin/health/fix    → Instance UP
 *   POST /api/v1/admin/health/toggle → Mevcut durumu tersine çevir
 */
@Component("simulatedHealth")
public class SimulatedHealthIndicator implements HealthIndicator {

    private volatile boolean healthy = true;
    private volatile String failureReason = "Manually triggered failure";
    private volatile LocalDateTime lastChanged = LocalDateTime.now();

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
        this.lastChanged = LocalDateTime.now();
    }

    public void setHealthy(boolean healthy, String reason) {
        this.healthy = healthy;
        this.failureReason = reason;
        this.lastChanged = LocalDateTime.now();
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void toggle() {
        setHealthy(!this.healthy);
    }

    @Override
    public Health health() {
        String changedAt = lastChanged.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        if (!healthy) {
            return Health.down()
                    .withDetail("reason", failureReason)
                    .withDetail("instanceId", System.getProperty("server.port", "unknown"))
                    .withDetail("lastChanged", changedAt)
                    .withDetail("hint", "POST /api/v1/admin/health/fix ile iyilestirin")
                    .build();
        }
        return Health.up()
                .withDetail("status", "All systems operational")
                .withDetail("instanceId", System.getProperty("server.port", "unknown"))
                .withDetail("lastChanged", changedAt)
                .build();
    }
}

