package com.gatehouse.gateway.filter;

import com.gatehouse.gateway.config.GatewayMetricsService;
import com.gatehouse.gateway.service.UsageTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class GlobalAccessLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GlobalAccessLoggingFilter.class);
    private static final Logger ACCESS_LOGGER = LoggerFactory.getLogger("ACCESS_LOG");

    private final GatewayMetricsService metricsService;
    private final UsageTrackingService usageTrackingService;

    public GlobalAccessLoggingFilter(GatewayMetricsService metricsService, UsageTrackingService usageTrackingService) {
        this.metricsService = metricsService;
        this.usageTrackingService = usageTrackingService;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE; // Intercept on response completion
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Long startTime = exchange.getAttribute(JwtValidationGlobalFilter.ATTR_START_TIME);
        if (startTime == null) {
            startTime = System.currentTimeMillis();
            exchange.getAttributes().put(JwtValidationGlobalFilter.ATTR_START_TIME, startTime);
        }

        final long start = startTime;

        return chain.filter(exchange).doFinally(signalType -> {
            long latencyMs = System.currentTimeMillis() - start;

            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            String method = request.getMethod().name();
            String path = request.getPath().value();

            HttpStatusCode statusCode = response.getStatusCode();
            int status = statusCode != null ? statusCode.value() : 500;

            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = route != null ? route.getId() : "direct";

            String clientId = exchange.getAttribute(JwtValidationGlobalFilter.ATTR_CLIENT_ID);
            String requestId = exchange.getAttribute(JwtValidationGlobalFilter.ATTR_REQUEST_ID);
            if (requestId == null) {
                requestId = response.getHeaders().getFirst("X-Request-Id");
            }

            long requestBytes = request.getHeaders().getContentLength();
            if (requestBytes < 0) requestBytes = 0;

            long responseBytes = response.getHeaders().getContentLength();
            if (responseBytes < 0) responseBytes = 0;

            // 1. Record Micrometer metrics
            metricsService.recordRequest(routeId, method, status, latencyMs);

            // 2. Structured Access Log
            try (var ignored = MDC.putCloseable("requestId", requestId != null ? requestId : "none")) {
                ACCESS_LOGGER.info(
                        "ACCESS_LOG timestamp=\"{}\" clientId=\"{}\" routeId=\"{}\" method=\"{}\" path=\"{}\" status={} latencyMs={} requestBytes={} responseBytes={} requestId=\"{}\"",
                        Instant.now(),
                        clientId != null ? clientId : "anonymous",
                        routeId,
                        method,
                        path,
                        status,
                        latencyMs,
                        requestBytes,
                        responseBytes,
                        requestId != null ? requestId : "none"
                );
            }

            // 3. Persist log to PostgreSQL asynchronously
            usageTrackingService.recordRequestLogAsync(
                    clientId, routeId, method, path, status, latencyMs, requestBytes, responseBytes
            );
        });
    }
}
