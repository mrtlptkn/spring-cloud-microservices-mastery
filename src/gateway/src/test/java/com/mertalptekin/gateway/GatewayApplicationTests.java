package com.mertalptekin.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"eureka.client.enabled=false",
				"spring.cloud.discovery.enabled=false"
		}
)
@AutoConfigureWebTestClient
@ActiveProfiles({"public", "circuitbreaker"})
class GatewayApplicationTests {

	@Autowired
	private GatewayProperties gatewayProperties;

	@Autowired
	private Environment environment;

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void contextLoads() {
		assertThat(gatewayProperties.getRoutes()).isNotEmpty();
	}

	@Test
	void circuitBreakerProfileLoadsExpectedResilience4jConfiguration() {
		assertThat(environment.getProperty("resilience4j.circuitbreaker.instances.productServiceBreaker.slidingWindowSize", Integer.class))
				.isEqualTo(3);
		assertThat(environment.getProperty("resilience4j.circuitbreaker.instances.productServiceBreaker.minimumNumberOfCalls", Integer.class))
				.isEqualTo(3);
		assertThat(environment.getProperty("resilience4j.circuitbreaker.instances.productServiceBreaker.waitDurationInOpenState"))
				.isEqualTo("30s");
		assertThat(environment.getProperty("resilience4j.timelimiter.instances.productServiceBreaker.timeoutDuration"))
				.isEqualTo("20s");
	}

	@Test
	void productRouteHasCircuitBreakerFallbackConfiguration() {
		var productRoute = gatewayProperties.getRoutes().stream()
				.filter(route -> "productClient".equals(route.getId()))
				.findFirst()
				.orElseThrow();

		var circuitBreakerFilter = productRoute.getFilters().stream()
				.filter(filter -> "CircuitBreaker".equals(filter.getName()))
				.findFirst()
				.orElseThrow();

		assertThat(circuitBreakerFilter.getArgs())
				.containsEntry("name", "productServiceBreaker")
				.containsEntry("fallbackUri", "forward:/fallback/product-service");
		assertThat(circuitBreakerFilter.getArgs().values())
				.contains("500", "503", "504");
	}

	@Test
	void fallbackEndpointReturnsExpectedMessageForManualTests() {
		var expectedMessage = "The Product Service is currently unavailable. Please try again later.";

		webTestClient.get()
				.uri("/fallback/product-service")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class).isEqualTo(expectedMessage);

		webTestClient.post()
				.uri("/fallback/product-service")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class).isEqualTo(expectedMessage);
	}

}
