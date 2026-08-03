package com.mertalptekin.orderservice.controller;
import com.mertalptekin.orderservice.health.SimulatedHealthIndicator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("api/v1/admin/health")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Health Simulation", description = "Instance saglik simulasyonu")
public class HealthToggleController {
    private final SimulatedHealthIndicator simulatedHealthIndicator;
    @PostMapping("/break")
    @Operation(summary = "Instance DOWN yap")
    public ResponseEntity<Map<String, Object>> breakHealth(@RequestParam(defaultValue = "Simulated failure") String reason) {
        simulatedHealthIndicator.setHealthy(false, reason);
        log.warn("Instance health SET TO DOWN. Reason: {}", reason);
        return ResponseEntity.ok(Map.of("action","HEALTH_BROKEN","status","DOWN","reason",reason));
    }
    @PostMapping("/fix")
    @Operation(summary = "Instance UP a don")
    public ResponseEntity<Map<String, Object>> fixHealth() {
        simulatedHealthIndicator.setHealthy(true);
        log.info("Instance health RESTORED TO UP.");
        return ResponseEntity.ok(Map.of("action","HEALTH_RESTORED","status","UP"));
    }
    @PostMapping("/toggle")
    @Operation(summary = "Toggle")
    public ResponseEntity<Map<String, Object>> toggleHealth() {
        simulatedHealthIndicator.toggle();
        boolean state = simulatedHealthIndicator.isHealthy();
        return ResponseEntity.ok(Map.of("action","HEALTH_TOGGLED","newStatus", state ? "UP" : "DOWN"));
    }
    @GetMapping("/status")
    @Operation(summary = "Simulasyon durumu")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of("simulatedHealthy", simulatedHealthIndicator.isHealthy()));
    }
}
