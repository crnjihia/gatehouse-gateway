package com.gatehouse.gateway;

import com.gatehouse.gateway.config.FlywayConfig;
import com.gatehouse.gateway.domain.model.RouteConfig;
import com.gatehouse.gateway.repository.ApiClientRepository;
import com.gatehouse.gateway.repository.RequestLogRepository;
import com.gatehouse.gateway.repository.RouteConfigRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = {GatehouseGatewayApplication.class, WireMockGatewayIntegrationTest.TestRouteConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=false",
                "spring.r2dbc.url=r2dbc:h2:mem:///testdb;DB_CLOSE_DELAY=-1"
        }
)
@ActiveProfiles("test")
public class WireMockGatewayIntegrationTest {

    private static WireMockServer wireMockServer;
    private static int wireMockPort;

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private FlywayConfig flywayConfig;

    @MockBean
    private ApiClientRepository apiClientRepository;

    @MockBean
    private RouteConfigRepository routeConfigRepository;

    @MockBean
    private RequestLogRepository requestLogRepository;

    @MockBean
    private ReactiveRedisConnectionFactory redisConnectionFactory;

    @MockBean
    private ReactiveStringRedisTemplate redisTemplate;

    @Configuration
    static class TestRouteConfig {
        @Bean
        public RouteLocator testRoutes(RouteLocatorBuilder builder) {
            return builder.routes()
                    .route("wiremock-test-route", r -> r.path("/test/users/**")
                            .filters(f -> f.stripPrefix(1)
                                    .circuitBreaker(c -> c.setName("testCircuitBreaker").setFallbackUri("forward:/fallback")))
                            .uri("http://localhost:" + wireMockPort))
                    .build();
        }
    }

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        wireMockPort = wireMockServer.port();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Autowired
    private com.gatehouse.gateway.security.JwtService jwtService;

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
        when(routeConfigRepository.findByEnabledTrueOrderByRouteOrderAsc()).thenReturn(Flux.empty());
    }

    @Test
    void shouldRouteToDownstreamSuccessfully() {
        String token = jwtService.generateToken("user-test", "client-enterprise", java.util.List.of("read"), 15);

        wireMockServer.stubFor(get(urlEqualTo("/users/42"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"42\",\"name\":\"Alice\"}")));

        webTestClient.get()
                .uri("/test/users/42")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.name").isEqualTo("Alice");
    }

    @Test
    void shouldTriggerCircuitBreakerFallbackWhenDownstreamFails() {
        String token = jwtService.generateToken("user-test", "client-enterprise", java.util.List.of("read"), 15);

        // Downstream failure: simulate downstream crash / connection reset
        wireMockServer.stubFor(get(urlEqualTo("/users/chaos"))
                .willReturn(aResponse()
                        .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        // Calling failing endpoint routes to fallback returning 503 ProblemDetail
        webTestClient.get()
                .uri("/test/users/chaos")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.title").isEqualTo("Service Unavailable");
    }
}
