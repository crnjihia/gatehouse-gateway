package com.gatehouse.gateway.filter;

import com.gatehouse.gateway.domain.model.ApiClient;
import com.gatehouse.gateway.domain.model.ClientStatus;
import com.gatehouse.gateway.security.ApiKeyService;
import com.gatehouse.gateway.security.JwtService;
import com.gatehouse.gateway.util.ProblemDetailUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
public class JwtValidationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtValidationGlobalFilter.class);

    public static final String ATTR_CLIENT_ID = "GATEHOUSE_CLIENT_ID";
    public static final String ATTR_CLIENT_PLAN = "GATEHOUSE_CLIENT_PLAN";
    public static final String ATTR_REQUEST_ID = "GATEHOUSE_REQUEST_ID";
    public static final String ATTR_START_TIME = "GATEHOUSE_START_TIME";

    private final JwtService jwtService;
    private final ApiKeyService apiKeyService;

    public JwtValidationGlobalFilter(JwtService jwtService, ApiKeyService apiKeyService) {
        this.jwtService = jwtService;
        this.apiKeyService = apiKeyService;
    }

    @Override
    public int getOrder() {
        return -50; // Execute early before rate limiters and route handlers
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        exchange.getAttributes().put(ATTR_START_TIME, startTime);

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Ensure Request-Id exists or generate UUID
        String requestId = request.getHeaders().getFirst("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        exchange.getAttributes().put(ATTR_REQUEST_ID, requestId);
        exchange.getResponse().getHeaders().set("X-Request-Id", requestId);

        // Bypass public and admin management endpoints
        if (isPublicPath(path)) {
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-Request-Id", requestId)
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        // 1. Try Bearer JWT Authentication
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authHeader.substring(7).trim();
            try {
                Claims claims = jwtService.validateToken(token);
                String sub = claims.getSubject();
                String clientId = claims.get("clientId", String.class);
                if (clientId == null || clientId.isBlank()) {
                    clientId = sub;
                }
                Object scopesObj = claims.get("scopes");
                String scopesStr = scopesObj != null ? scopesObj.toString() : "";

                exchange.getAttributes().put(ATTR_CLIENT_ID, clientId);

                ServerHttpRequest mutatedRequest = request.mutate()
                        .header("X-Client-Id", clientId)
                        .header("X-Client-Scopes", scopesStr)
                        .header("X-Request-Id", requestId)
                        .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("JWT validation failed for request [{} {}]: {}", request.getMethod(), path, e.getMessage());
                return ProblemDetailUtil.writeProblemDetail(
                        exchange.getResponse(),
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized - Invalid JWT",
                        "Token validation failed: " + e.getMessage(),
                        path
                );
            }
        }

        // 2. Try API Key Authentication (X-API-Key or Authorization: ApiKey <key>)
        String apiKey = request.getHeaders().getFirst("X-API-Key");
        if ((apiKey == null || apiKey.isBlank()) && authHeader != null && authHeader.regionMatches(true, 0, "ApiKey ", 0, 7)) {
            apiKey = authHeader.substring(7).trim();
        }

        final String finalRequestId = requestId;
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKeyService.findByApiKey(apiKey)
                    .flatMap(client -> {
                        if (client.getStatus() != ClientStatus.ACTIVE) {
                            return ProblemDetailUtil.writeProblemDetail(
                                    exchange.getResponse(),
                                    HttpStatus.UNAUTHORIZED,
                                    "Unauthorized - Account Not Active",
                                    "API Client account status is " + client.getStatus(),
                                    path
                            );
                        }

                        exchange.getAttributes().put(ATTR_CLIENT_ID, client.getId());
                        exchange.getAttributes().put(ATTR_CLIENT_PLAN, client.getPlan().name());

                        ServerHttpRequest mutatedRequest = request.mutate()
                                .header("X-Client-Id", client.getId())
                                .header("X-Client-Plan", client.getPlan().name())
                                .header("X-Request-Id", finalRequestId)
                                .build();

                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    })
                    .switchIfEmpty(
                            ProblemDetailUtil.writeProblemDetail(
                                    exchange.getResponse(),
                                    HttpStatus.UNAUTHORIZED,
                                    "Unauthorized - Invalid API Key",
                                    "The provided API key does not exist or has been revoked.",
                                    path
                            )
                    );
        }

        // No credentials provided on protected endpoint
        return ProblemDetailUtil.writeProblemDetail(
                exchange.getResponse(),
                HttpStatus.UNAUTHORIZED,
                "Unauthorized - Authentication Required",
                "Full authentication is required. Provide 'Authorization: Bearer <token>' or 'X-API-Key: <key>'.",
                path
        );
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/actuator") ||
                path.startsWith("/admin") ||
                path.startsWith("/fallback") ||
                path.startsWith("/auth") ||
                path.equals("/") ||
                path.equals("/health");
    }
}
