package com.gatehouse.gateway.filter;

import com.gatehouse.gateway.config.GatewayMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"unchecked", "rawtypes"})
class TokenBucketRateLimitFilterTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private RedisScript<List> rateLimiterScript;

    @Mock
    private GatewayMetricsService metricsService;

    @Mock
    private GatewayFilterChain chain;

    private TokenBucketRateLimitFilterFactory factory;

    @BeforeEach
    void setUp() {
        factory = new TokenBucketRateLimitFilterFactory(metricsService, redisTemplate, rateLimiterScript);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void shouldAllowRequestWhenUnderLimitAndSetHeaders() {
        TokenBucketRateLimitFilterFactory.Config config = new TokenBucketRateLimitFilterFactory.Config();
        config.setRatePerMin(100);
        GatewayFilter filter = factory.apply(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(JwtValidationGlobalFilter.ATTR_CLIENT_ID, "client-test-1");

        // Redis script returns: [allowed=1, rateRemaining=99, quotaRemaining=999, resetSeconds=55]
        List<Object> scriptResponse = List.of(1L, 99L, 999L, 55L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(scriptResponse));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("99");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Reset")).isEqualTo("55");
    }

    @Test
    void shouldRejectWith429WhenRateLimitExceeded() {
        TokenBucketRateLimitFilterFactory.Config config = new TokenBucketRateLimitFilterFactory.Config();
        config.setRatePerMin(60);
        GatewayFilter filter = factory.apply(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(JwtValidationGlobalFilter.ATTR_CLIENT_ID, "client-test-1");

        // Redis script returns: [allowed=0, rateRemaining=0, quotaRemaining=500, resetSeconds=30]
        List<Object> scriptResponse = List.of(0L, 0L, 500L, 30L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(scriptResponse));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("30");
        verify(metricsService).recordRateLimitRejected("client-test-1");
    }

    @Test
    void shouldRejectWith429WhenDailyQuotaExceeded() {
        TokenBucketRateLimitFilterFactory.Config config = new TokenBucketRateLimitFilterFactory.Config();
        GatewayFilter filter = factory.apply(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(JwtValidationGlobalFilter.ATTR_CLIENT_ID, "client-test-1");

        // Redis script returns: [allowed=-1, rateRemaining=0, quotaRemaining=0, resetSeconds=3600]
        List<Object> scriptResponse = List.of(-1L, 0L, 0L, 3600L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(scriptResponse));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("3600");
        verify(metricsService).recordRateLimitRejected("client-test-1");
    }
}
