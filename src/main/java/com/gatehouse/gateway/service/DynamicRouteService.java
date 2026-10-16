package com.gatehouse.gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatehouse.gateway.domain.model.RouteConfig;
import com.gatehouse.gateway.repository.RouteConfigRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@EnableScheduling
public class DynamicRouteService {

    private static final Logger log = LoggerFactory.getLogger(DynamicRouteService.class);

    private final RouteConfigRepository routeConfigRepository;
    private final RouteDefinitionWriter routeDefinitionWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // Track active route IDs in memory to cleanly evict removed ones
    private final ConcurrentHashMap.KeySetView<String, Boolean> activeRouteIds = ConcurrentHashMap.newKeySet();

    public DynamicRouteService(RouteConfigRepository routeConfigRepository,
                               RouteDefinitionWriter routeDefinitionWriter,
                               ApplicationEventPublisher eventPublisher,
                               ObjectMapper objectMapper) {
        this.routeConfigRepository = routeConfigRepository;
        this.routeDefinitionWriter = routeDefinitionWriter;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready. Initializing dynamic route loading from database...");
        reloadRoutes().subscribe(
                null,
                error -> log.warn("Initial route loading encountered an issue (e.g. database not yet reachable): {}", error.getMessage()),
                () -> log.info("Initial dynamic routes loaded.")
        );
    }

    /**
     * Poll database every 30 seconds for dynamic route changes without restart.
     */
    @Scheduled(fixedDelayString = "${gatehouse.routing.refresh-interval-seconds:30}000", initialDelay = 30000)
    public void scheduledRouteRefresh() {
        reloadRoutes().subscribe();
    }

    public Mono<Void> reloadRoutes() {
        log.info("Refreshing dynamic routes from PostgreSQL...");
        var routesFlux = routeConfigRepository.findByEnabledTrueOrderByRouteOrderAsc();
        if (routesFlux == null) {
            return Mono.empty();
        }
        return routesFlux
                .collectList()
                .flatMap(routes -> {
                    List<String> newRouteIds = new ArrayList<>();
                    return reactor.core.publisher.Flux.fromIterable(routes)
                            .flatMap(routeConfig -> {
                                RouteDefinition definition = toRouteDefinition(routeConfig);
                                if (definition == null) {
                                    return Mono.empty();
                                }
                                newRouteIds.add(definition.getId());
                                activeRouteIds.add(definition.getId());
                                return routeDefinitionWriter.save(Mono.just(definition));
                            })
                            .then(evictRemovedRoutes(newRouteIds))
                            .doOnSuccess(v -> {
                                log.info("Loaded {} active dynamic routes. Publishing RefreshRoutesEvent...", routes.size());
                                eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                            });
                })
                .doOnError(err -> log.error("Error refreshing dynamic routes: {}", err.getMessage()));
    }

    private Mono<Void> evictRemovedRoutes(List<String> newRouteIds) {
        List<String> routesToDelete = activeRouteIds.stream()
                .filter(id -> !newRouteIds.contains(id))
                .toList();

        if (routesToDelete.isEmpty()) {
            return Mono.empty();
        }

        return reactor.core.publisher.Flux.fromIterable(routesToDelete)
                .flatMap(id -> {
                    activeRouteIds.remove(id);
                    return routeDefinitionWriter.delete(Mono.just(id))
                            .onErrorResume(e -> Mono.empty()); // Ignore if already absent
                })
                .then();
    }

    public RouteDefinition toRouteDefinition(RouteConfig config) {
        try {
            RouteDefinition definition = new RouteDefinition();
            definition.setId(config.getRouteId());
            definition.setUri(URI.create(config.getUri()));
            definition.setOrder(config.getRouteOrder() != null ? config.getRouteOrder() : 0);

            // Parse Predicates JSON
            List<PredicateDefinition> predicates = parsePredicates(config.getPredicates());
            definition.setPredicates(predicates);

            // Parse Filters JSON
            List<FilterDefinition> filters = parseFilters(config.getFilters());
            definition.setFilters(filters);

            return definition;
        } catch (Exception e) {
            log.error("Failed to parse RouteConfig ID: {} routeId: {}. Reason: {}",
                    config.getId(), config.getRouteId(), e.getMessage());
            return null;
        }
    }

    private List<PredicateDefinition> parsePredicates(String predicatesJson) {
        if (predicatesJson == null || predicatesJson.isBlank() || predicatesJson.trim().equals("[]")) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(predicatesJson, new TypeReference<>() {});
            List<PredicateDefinition> result = new ArrayList<>();
            for (Map<String, Object> map : list) {
                if (map.containsKey("text")) {
                    result.add(new PredicateDefinition(map.get("text").toString()));
                } else if (map.containsKey("name")) {
                    PredicateDefinition pd = new PredicateDefinition();
                    pd.setName((String) map.get("name"));
                    if (map.containsKey("args") && map.get("args") instanceof Map<?, ?> argsMap) {
                        argsMap.forEach((k, v) -> pd.addArg(k.toString(), v != null ? v.toString() : ""));
                    }
                    result.add(pd);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse predicates JSON: '{}'. Error: {}", predicatesJson, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<FilterDefinition> parseFilters(String filtersJson) {
        if (filtersJson == null || filtersJson.isBlank() || filtersJson.trim().equals("[]")) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(filtersJson, new TypeReference<>() {});
            List<FilterDefinition> result = new ArrayList<>();
            for (Map<String, Object> map : list) {
                if (map.containsKey("text")) {
                    result.add(new FilterDefinition(map.get("text").toString()));
                } else if (map.containsKey("name")) {
                    FilterDefinition fd = new FilterDefinition();
                    fd.setName((String) map.get("name"));
                    if (map.containsKey("args") && map.get("args") instanceof Map<?, ?> argsMap) {
                        argsMap.forEach((k, v) -> fd.addArg(k.toString(), v != null ? v.toString() : ""));
                    }
                    result.add(fd);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse filters JSON: '{}'. Error: {}", filtersJson, e.getMessage());
            return Collections.emptyList();
        }
    }
}
