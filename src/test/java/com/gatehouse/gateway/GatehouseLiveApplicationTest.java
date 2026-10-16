package com.gatehouse.gateway;

import com.gatehouse.gateway.config.FlywayConfig;
import com.gatehouse.gateway.controller.AdminClientController.CreateClientRequest;
import com.gatehouse.gateway.domain.model.ApiClient;
import com.gatehouse.gateway.domain.model.ClientPlan;
import com.gatehouse.gateway.domain.model.ClientStatus;
import com.gatehouse.gateway.repository.ApiClientRepository;
import com.gatehouse.gateway.repository.RequestLogRepository;
import com.gatehouse.gateway.repository.RouteConfigRepository;
import com.gatehouse.gateway.security.JwtService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = {GatehouseGatewayApplication.class, GatehouseLiveApplicationTest.LiveTestRouteConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=false",
                "spring.r2dbc.url=r2dbc:h2:mem:///live_testdb;DB_CLOSE_DELAY=-1",
                "gatehouse.security.admin.secret-token=test-admin-master-key"
        }
)
@ActiveProfiles("test")
public class GatehouseLiveApplicationTest {

    private static WireMockServer wireMockServer;
    private static int wireMockPort;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtService jwtService;

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
    static class LiveTestRouteConfig {
        @Bean
        public RouteLocator liveTestRoutes(RouteLocatorBuilder builder,
                                           com.gatehouse.gateway.filter.RequestResponseTransformationFilterFactory transformFilter) {
            var filterConfig = new com.gatehouse.gateway.filter.RequestResponseTransformationFilterFactory.Config();
            filterConfig.setMaskFields("cardNumber,cvv");

            return builder.routes()
                    .route("payment-route", r -> r.path("/test/payments/**")
                            .filters(f -> f.stripPrefix(1)
                                    .filter(transformFilter.apply(filterConfig)))
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

    @BeforeEach
    void setupMocks() {
        wireMockServer.resetAll();
        when(routeConfigRepository.findByEnabledTrueOrderByRouteOrderAsc()).thenReturn(Flux.empty());
    }

    @Test
    @DisplayName("Actuator health check returns status UP")
    void actuatorHealthShouldReturnUp() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("Unauthenticated request to protected route returns 401 ProblemDetail")
    void unauthenticatedRequestReturns401ProblemDetail() {
        webTestClient.get()
                .uri("/test/payments/history")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.title").isEqualTo("Unauthorized - Authentication Required");
    }

    @Test
    @DisplayName("Admin client creation with valid Master Secret Token returns 201 and apiKey")
    void adminCanCreateClientWithMasterSecretToken() {
        ApiClient savedClient = ApiClient.builder()
                .id("client-123")
                .name("Acme Corp")
                .apiKeyHash("hashed-key")
                .apiKeyPrefix("gh_live_")
                .apiSecretHash("hashed-secret")
                .ownerEmail("admin@acme.com")
                .plan(ClientPlan.PRO)
                .rateLimitPerMin(600)
                .quotaPerDay(50000L)
                .status(ClientStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(apiClientRepository.save(any(ApiClient.class))).thenReturn(Mono.just(savedClient));

        CreateClientRequest request = new CreateClientRequest();
        request.setName("Acme Corp");
        request.setOwnerEmail("admin@acme.com");
        request.setPlan(ClientPlan.PRO);

        webTestClient.post()
                .uri("/admin/clients")
                .header("X-Admin-Key", "test-admin-master-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Acme Corp")
                .jsonPath("$.plan").isEqualTo("PRO")
                .jsonPath("$.rateLimitPerMin").isEqualTo(600)
                .jsonPath("$.rawApiKey").isNotEmpty();
    }

    @Test
    @DisplayName("Admin API rejects requests without admin token with 403 Forbidden")
    void adminEndpointsRejectUnauthorized() {
        webTestClient.get()
                .uri("/admin/clients")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Forbidden - Admin Access Required");
    }

    @Test
    @DisplayName("Admin route reload triggers 200 response")
    void adminCanReloadRoutes() {
        webTestClient.post()
                .uri("/admin/routes/reload")
                .header("X-Admin-Key", "test-admin-master-key")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Fallback endpoint returns 503 ProblemDetail")
    void fallbackEndpointReturns503() {
        webTestClient.get()
                .uri("/fallback")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.title").isEqualTo("Service Unavailable");
    }

    @Test
    @DisplayName("End-to-End JWT auth, header injection, downstream proxying, and PCI body masking")
    void authenticatedRequestProxiesAndMasksPciData() {
        String token = jwtService.generateToken("user-payments", "client-enterprise", List.of("pay.read"), 10);

        wireMockServer.stubFor(get(urlEqualTo("/payments/history"))
                .withHeader("X-Client-Id", equalTo("client-enterprise"))
                .withHeader("X-Client-Scopes", com.github.tomakehurst.wiremock.client.WireMock.containing("pay.read"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"account\":\"12345\",\"cardNumber\":\"4111222233334444\",\"cvv\":\"999\",\"amount\":150.00}")));

        webTestClient.get()
                .uri("/test/payments/history")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.account").isEqualTo("12345")
                .jsonPath("$.amount").isEqualTo(150.00)
                .jsonPath("$.cardNumber").isEqualTo("****-****-****-4444")
                .jsonPath("$.cvv").isEqualTo("******");
    }
}
