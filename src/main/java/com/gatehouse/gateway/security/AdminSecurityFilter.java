package com.gatehouse.gateway.security;

import com.gatehouse.gateway.config.GatehouseProperties;
import com.gatehouse.gateway.util.ProblemDetailUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class AdminSecurityFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityFilter.class);

    private final GatehouseProperties properties;
    private final JwtService jwtService;

    public AdminSecurityFilter(GatehouseProperties properties, JwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    @Override
    public int getOrder() {
        return -100; // Run early
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/admin")) {
            return chain.filter(exchange);
        }

        // 1. Check Master Admin Key header
        String adminKey = exchange.getRequest().getHeaders().getFirst("X-Admin-Key");
        String configuredMasterKey = properties.getSecurity().getAdmin().getSecretToken();
        if (adminKey != null && adminKey.equals(configuredMasterKey)) {
            return chain.filter(exchange);
        }

        // 2. Check Admin JWT (role: ADMIN)
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authHeader.substring(7).trim();
            try {
                Claims claims = jwtService.validateToken(token);
                Object roleClaim = claims.get("role");
                Object rolesClaim = claims.get("roles");
                boolean isAdmin = "ADMIN".equalsIgnoreCase(String.valueOf(roleClaim));
                if (!isAdmin && rolesClaim instanceof List<?> list) {
                    isAdmin = list.stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(String.valueOf(r)));
                }

                if (isAdmin) {
                    return chain.filter(exchange);
                }
            } catch (Exception e) {
                log.warn("Admin JWT verification rejected: {}", e.getMessage());
            }
        }

        return ProblemDetailUtil.writeProblemDetail(
                exchange.getResponse(),
                HttpStatus.FORBIDDEN,
                "Forbidden - Admin Access Required",
                "Access to /admin endpoints requires a valid Admin JWT with role 'ADMIN' or 'X-Admin-Key' header.",
                path
        );
    }
}
