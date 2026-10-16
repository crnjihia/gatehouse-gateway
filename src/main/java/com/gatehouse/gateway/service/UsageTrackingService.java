package com.gatehouse.gateway.service;

import com.gatehouse.gateway.domain.model.RequestLog;
import com.gatehouse.gateway.repository.RequestLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.UUID;

@Service
public class UsageTrackingService {

    private static final Logger log = LoggerFactory.getLogger(UsageTrackingService.class);

    private final RequestLogRepository requestLogRepository;

    public UsageTrackingService(RequestLogRepository requestLogRepository) {
        this.requestLogRepository = requestLogRepository;
    }

    /**
     * Non-blocking async recording of request log to partitioned PostgreSQL table.
     */
    public void recordRequestLogAsync(String clientId, String routeId, String method,
                                     String path, int statusCode, long latencyMs,
                                     long requestBytes, long responseBytes) {
        RequestLog logEntry = RequestLog.builder()
                .id(UUID.randomUUID().toString())
                .clientId(clientId)
                .routeId(routeId != null ? routeId : "unmatched")
                .method(method)
                .path(path)
                .statusCode(statusCode)
                .latencyMs(latencyMs)
                .requestBytes(requestBytes)
                .responseBytes(responseBytes)
                .timestamp(Instant.now())
                .build();

        var saveMono = requestLogRepository.save(logEntry);
        if (saveMono != null) {
            saveMono.subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            saved -> {},
                            error -> log.debug("Async request log persist failure: {}", error.getMessage())
                    );
        }
    }
}
