package com.gatehouse.gateway.filter;

import com.gatehouse.gateway.config.GatewayMetricsService;
import com.gatehouse.gateway.domain.model.ClientPlan;
import com.gatehouse.gateway.util.ProblemDetailUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class TokenBucketRateLimitFilterFactory
        extends AbstractGatewayFilterFactory<TokenBucketRateLimitFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimitFilterFactory.class);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> rateLimiterScript;
    private final GatewayMetricsService metricsService;

    @org.springframework.beans.factory.annotation.Autowired
    public TokenBucketRateLimitFilterFactory(
            GatewayMetricsService metricsService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            ReactiveRedisTemplate<String, String> redisTemplate,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            RedisScript<List> rateLimiterScript) {
        super(Config.class);
        this.metricsService = metricsService;
        this.redisTemplate = redisTemplate;
        this.rateLimiterScript = rateLimiterScript;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            if (redisTemplate == null || rateLimiterScript == null) {
                return chain.filter(exchange);
            }
            String clientId = exchange.getAttribute(JwtValidationGlobalFilter.ATTR_CLIENT_ID);
            if (clientId == null || clientId.isBlank()) {
                var remoteAddress = exchange.getRequest().getRemoteAddress();
                clientId = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "anonymous";
            }

            // Determine plan limits
            String planName = exchange.getAttribute(JwtValidationGlobalFilter.ATTR_CLIENT_PLAN);
            ClientPlan plan = ClientPlan.FREE;
            if (planName != null) {
                try {
                    plan = ClientPlan.valueOf(planName);
                } catch (IllegalArgumentException ignored) {}
            }

            int rateLimit = (config.getRatePerMin() != null && config.getRatePerMin() > 0)
                    ? config.getRatePerMin()
                    : plan.getDefaultRateLimitPerMin();

            long quotaLimit = (config.getQuotaPerDay() != null && config.getQuotaPerDay() > 0)
                    ? config.getQuotaPerDay()
                    : plan.getDefaultQuotaPerDay();

            long currentEpochMinute = Instant.now().getEpochSecond() / 60;
            String dateStr = LocalDate.now(ZoneOffset.UTC).toString();

            String rateKey = "rate:" + clientId + ":" + currentEpochMinute;
            String quotaKey = "quota:" + clientId + ":" + dateStr;

            List<String> keys = List.of(rateKey, quotaKey);
            String[] args = new String[]{
                    String.valueOf(rateLimit),
                    String.valueOf(quotaLimit),
                    "60",    // 60-second TTL for minute bucket
                    "86400"  // 24-hour TTL for daily quota bucket
            };

            final String finalClientId = clientId;
            final int finalRateLimit = rateLimit;

            return redisTemplate.execute(rateLimiterScript, keys, List.of(args))
                    .next()
                    .flatMap(rawResult -> {
                        List<?> resultList = (List<?>) rawResult;
                        long allowed = ((Number) resultList.get(0)).longValue();
                        long rateRemaining = ((Number) resultList.get(1)).longValue();
                        long quotaRemaining = ((Number) resultList.get(2)).longValue();
                        long resetSeconds = ((Number) resultList.get(3)).longValue();

                        var responseHeaders = exchange.getResponse().getHeaders();
                        responseHeaders.set("X-RateLimit-Limit", String.valueOf(finalRateLimit));
                        responseHeaders.set("X-RateLimit-Remaining", String.valueOf(rateRemaining));
                        responseHeaders.set("X-RateLimit-Reset", String.valueOf(resetSeconds));

                        if (allowed == 1) {
                            return chain.filter(exchange);
                        }

                        // Rate limit or quota exceeded
                        metricsService.recordRateLimitRejected(finalClientId);
                        responseHeaders.set("Retry-After", String.valueOf(Math.max(1, resetSeconds)));

                        String reason = (allowed == -1)
                                ? "Daily API call quota of " + quotaLimit + " requests exhausted. Quota resets in " + resetSeconds + " seconds."
                                : "Rate limit of " + finalRateLimit + " req/min exceeded. Please retry in " + resetSeconds + " seconds.";

                        return ProblemDetailUtil.writeProblemDetail(
                                exchange.getResponse(),
                                HttpStatus.TOO_MANY_REQUESTS,
                                "Too Many Requests",
                                reason,
                                exchange.getRequest().getPath().value()
                        );
                    })
                    .onErrorResume(ex -> {
                        // In case Redis is temporarily unreachable, fail-open to preserve availability
                        log.warn("Redis rate limiter unavailable: {}. Allowing request through fail-open.", ex.getMessage());
                        return chain.filter(exchange);
                    });
        };
    }

    public static class Config {
        private Integer ratePerMin;
        private Long quotaPerDay;

        public Config() {}

        public Integer getRatePerMin() {
            return ratePerMin;
        }

        public void setRatePerMin(Integer ratePerMin) {
            this.ratePerMin = ratePerMin;
        }

        public Long getQuotaPerDay() {
            return quotaPerDay;
        }

        public void setQuotaPerDay(Long quotaPerDay) {
            this.quotaPerDay = quotaPerDay;
        }
    }
}
