package com.gatehouse.gateway.repository;

import com.gatehouse.gateway.domain.model.RouteConfig;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface RouteConfigRepository extends ReactiveCrudRepository<RouteConfig, String> {

    Flux<RouteConfig> findByEnabledTrueOrderByRouteOrderAsc();

    Mono<RouteConfig> findByRouteId(String routeId);
}
