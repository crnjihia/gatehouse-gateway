package com.gatehouse.gateway.filter;

import com.gatehouse.gateway.config.GatehouseProperties;
import com.gatehouse.gateway.domain.model.ApiClient;
import com.gatehouse.gateway.domain.model.ClientPlan;
import com.gatehouse.gateway.domain.model.ClientStatus;
import com.gatehouse.gateway.security.ApiKeyService;
import com.gatehouse.gateway.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtValidationGlobalFilterTest {

    private GatehouseProperties properties;
    private JwtService jwtService;

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private GatewayFilterChain chain;

    private JwtValidationGlobalFilter filter;

    @BeforeEach
    void setUp() {
        properties = new GatehouseProperties();
        jwtService = new JwtService(properties);
        jwtService.init(); // generates internal RSA keypair
        filter = new JwtValidationGlobalFilter(jwtService, apiKeyService);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void shouldAllowPublicPathWithoutAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void shouldRejectProtectedEndpointWhenNoAuthProvided() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/123").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAcceptValidJwtAndPropagateDownstreamHeaders() {
        String token = jwtService.generateToken("user-sub-1", "client-acme-42", List.of("read", "write"), 15);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getAttribute(JwtValidationGlobalFilter.ATTR_CLIENT_ID).toString()).isEqualTo("client-acme-42");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id")).isNotBlank();
        verify(chain).filter(any());
    }

    @Test
    void shouldRejectInvalidSignatureJwt() {
        String invalidToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMifQ.InvalidSignatureString";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAuthenticateValidApiKey() {
        String rawKey = "gh_live_valid_test_api_key_123456";
        ApiClient client = ApiClient.builder()
                .id("client-enterprise-99")
                .name("Test Client")
                .plan(ClientPlan.ENTERPRISE)
                .status(ClientStatus.ACTIVE)
                .build();

        when(apiKeyService.findByApiKey(rawKey)).thenReturn(Mono.just(client));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/123")
                .header("X-API-Key", rawKey)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getAttribute(JwtValidationGlobalFilter.ATTR_CLIENT_ID).toString()).isEqualTo("client-enterprise-99");
        assertThat(exchange.getAttribute(JwtValidationGlobalFilter.ATTR_CLIENT_PLAN).toString()).isEqualTo("ENTERPRISE");
        verify(chain).filter(any());
    }
}
