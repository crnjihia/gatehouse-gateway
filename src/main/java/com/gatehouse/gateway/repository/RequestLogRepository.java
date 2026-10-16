package com.gatehouse.gateway.repository;

import com.gatehouse.gateway.domain.model.RequestLog;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
public interface RequestLogRepository extends ReactiveCrudRepository<RequestLog, String> {

    Flux<RequestLog> findByClientIdAndTimestampBetweenOrderByTimestampDesc(
            String clientId, Instant from, Instant to
    );

    @Query("SELECT COUNT(*) FROM request_logs WHERE client_id = :clientId AND timestamp >= :from AND timestamp <= :to")
    Mono<Long> countByClientIdAndTimestampBetween(String clientId, Instant from, Instant to);

    @Query("SELECT AVG(latency_ms) FROM request_logs WHERE client_id = :clientId AND timestamp >= :from AND timestamp <= :to")
    Mono<Double> averageLatencyByClientIdAndTimestampBetween(String clientId, Instant from, Instant to);
}
