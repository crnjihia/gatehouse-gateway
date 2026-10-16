package com.gatehouse.gateway.repository;

import com.gatehouse.gateway.domain.model.ApiClient;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ApiClientRepository extends ReactiveCrudRepository<ApiClient, String> {

    Mono<ApiClient> findByApiKeyHash(String apiKeyHash);

    Mono<ApiClient> findByOwnerEmail(String ownerEmail);
}
