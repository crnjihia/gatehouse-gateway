package com.gatehouse.gateway.controller;

import com.gatehouse.gateway.domain.model.RouteConfig;
import com.gatehouse.gateway.repository.RouteConfigRepository;
import com.gatehouse.gateway.service.DynamicRouteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/routes")
public class AdminRouteController {

    private static final Logger log = LoggerFactory.getLogger(AdminRouteController.class);

    private final RouteConfigRepository routeRepository;
    private final DynamicRouteService dynamicRouteService;

    public AdminRouteController(RouteConfigRepository routeRepository, DynamicRouteService dynamicRouteService) {
        this.routeRepository = routeRepository;
        this.dynamicRouteService = dynamicRouteService;
    }

    public static class RouteRequest {
        @NotBlank
        private String routeId;
        @NotBlank
        private String uri;
        private String predicates = "[]";
        private String filters = "[]";
        private Integer routeOrder = 0;
        private Boolean enabled = true;
        private String description;

        public RouteRequest() {}

        public String getRouteId() { return routeId; }
        public void setRouteId(String routeId) { this.routeId = routeId; }
        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }
        public String getPredicates() { return predicates; }
        public void setPredicates(String predicates) { this.predicates = predicates; }
        public String getFilters() { return filters; }
        public void setFilters(String filters) { this.filters = filters; }
        public Integer getRouteOrder() { return routeOrder; }
        public void setRouteOrder(Integer routeOrder) { this.routeOrder = routeOrder; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RouteConfig> createRoute(@Valid @RequestBody RouteRequest req) {
        Instant now = Instant.now();
        RouteConfig route = RouteConfig.builder()
                .id(UUID.randomUUID().toString())
                .routeId(req.getRouteId())
                .uri(req.getUri())
                .predicates(req.getPredicates() != null ? req.getPredicates() : "[]")
                .filters(req.getFilters() != null ? req.getFilters() : "[]")
                .routeOrder(req.getRouteOrder() != null ? req.getRouteOrder() : 0)
                .enabled(req.getEnabled() != null ? req.getEnabled() : true)
                .description(req.getDescription())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return routeRepository.save(route)
                .flatMap(saved -> dynamicRouteService.reloadRoutes().thenReturn(saved));
    }

    @GetMapping
    public Flux<RouteConfig> listRoutes() {
        return routeRepository.findAll();
    }

    @GetMapping("/{id}")
    public Mono<RouteConfig> getRoute(@PathVariable String id) {
        return routeRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found")));
    }

    @PutMapping("/{id}")
    public Mono<RouteConfig> updateRoute(@PathVariable String id, @Valid @RequestBody RouteRequest req) {
        return routeRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found")))
                .flatMap(route -> {
                    route.setRouteId(req.getRouteId());
                    route.setUri(req.getUri());
                    if (req.getPredicates() != null) route.setPredicates(req.getPredicates());
                    if (req.getFilters() != null) route.setFilters(req.getFilters());
                    if (req.getRouteOrder() != null) route.setRouteOrder(req.getRouteOrder());
                    if (req.getEnabled() != null) route.setEnabled(req.getEnabled());
                    if (req.getDescription() != null) route.setDescription(req.getDescription());
                    route.setUpdatedAt(Instant.now());

                    return routeRepository.save(route);
                })
                .flatMap(saved -> dynamicRouteService.reloadRoutes().thenReturn(saved));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteRoute(@PathVariable String id) {
        return routeRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found")))
                .flatMap(route -> routeRepository.delete(route))
                .then(dynamicRouteService.reloadRoutes());
    }

    @PostMapping("/reload")
    public Mono<Map<String, Object>> reloadRoutes() {
        return dynamicRouteService.reloadRoutes()
                .thenReturn(Map.of(
                        "status", "SUCCESS",
                        "message", "Dynamic route reload triggered and RefreshRoutesEvent published",
                        "timestamp", Instant.now().toString()
                ));
    }
}
