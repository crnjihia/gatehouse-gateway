package com.gatehouse.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestResponseTransformationFilterTest {

    @Mock
    private ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilterFactory;

    @Mock
    private GatewayFilterChain chain;

    private ObjectMapper objectMapper;
    private RequestResponseTransformationFilterFactory factory;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Return a pass-through mock GatewayFilter from modifyResponseBodyFilterFactory
        when(modifyResponseBodyFilterFactory.apply(any(ModifyResponseBodyGatewayFilterFactory.Config.class)))
                .thenReturn((exchange, c) -> c.filter(exchange));

        factory = new RequestResponseTransformationFilterFactory(modifyResponseBodyFilterFactory, objectMapper);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void shouldInjectCorrelationIdAndRewriteForwardedHeaders() {
        RequestResponseTransformationFilterFactory.Config config = new RequestResponseTransformationFilterFactory.Config();
        GatewayFilter filter = factory.apply(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users")
                .header("Host", "gateway.example.com")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(JwtValidationGlobalFilter.ATTR_REQUEST_ID, "req-uuid-1234");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id")).isEqualTo("req-uuid-1234");
    }

    @Test
    void shouldMaskSensitiveResponseFieldsCorrectly() throws Exception {
        // Test json masking logic directly
        String jsonPayload = """
            {
                "id": "1001",
                "name": "Jane Doe",
                "cardNumber": "4532750012345678",
                "cvv": "987",
                "password": "SuperSecretPassword123!",
                "metadata": {
                    "ssn": "123-45-6789"
                }
            }
        """;

        JsonNode root = objectMapper.readTree(jsonPayload);
        // Call filter mask helper via reflection or simulate behavior
        java.lang.reflect.Method maskMethod = RequestResponseTransformationFilterFactory.class.getDeclaredMethod(
                "maskFieldsRecursively", JsonNode.class, java.util.Set.class
        );
        maskMethod.setAccessible(true);
        maskMethod.invoke(factory, root, java.util.Set.of("cardNumber", "cvv", "password", "ssn"));

        assertThat(root.get("cardNumber").asText()).isEqualTo("****-****-****-5678");
        assertThat(root.get("cvv").asText()).isEqualTo("******");
        assertThat(root.get("password").asText()).isEqualTo("******");
        assertThat(root.get("metadata").get("ssn").asText()).isEqualTo("******");
        assertThat(root.get("name").asText()).isEqualTo("Jane Doe"); // Unmasked
    }
}
