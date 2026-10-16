package com.gatehouse.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class GatewayMetricsService {

    private static final Logger log = LoggerFactory.getLogger(GatewayMetricsService.class);

    private final MeterRegistry meterRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public GatewayMetricsService(MeterRegistry meterRegistry, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.meterRegistry = meterRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    private final ConcurrentHashMap<String, Counter> requestCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> requestTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> rateLimitCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> circuitBreakerGauges = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Bind existing and newly registered circuit breakers to gauges
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::registerCircuitBreakerGauge);
        circuitBreakerRegistry.getEventPublisher().onEntryAdded(event -> registerCircuitBreakerGauge(event.getAddedEntry()));
    }

    public void recordRequest(String routeId, String method, int statusCode, long latencyMs) {
        String safeRoute = (routeId != null && !routeId.isBlank()) ? routeId : "unknown";
        String safeMethod = (method != null) ? method : "UNKNOWN";
        String statusStr = String.valueOf(statusCode);

        String counterKey = safeRoute + ":" + safeMethod + ":" + statusStr;
        requestCounters.computeIfAbsent(counterKey, k -> Counter.builder("gateway.requests.total")
                .description("Total number of requests routed through the gateway")
                .tag("route", safeRoute)
                .tag("method", safeMethod)
                .tag("status", statusStr)
                .register(meterRegistry)
        ).increment();

        String timerKey = safeRoute + ":" + safeMethod + ":" + statusStr;
        requestTimers.computeIfAbsent(timerKey, k -> Timer.builder("gateway.request.duration")
                .description("Execution latency of gateway requests")
                .tag("route", safeRoute)
                .tag("method", safeMethod)
                .tag("status", statusStr)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry)
        ).record(latencyMs, TimeUnit.MILLISECONDS);
    }

    public void recordRateLimitRejected(String clientId) {
        String safeClient = (clientId != null && !clientId.isBlank()) ? clientId : "anonymous";
        rateLimitCounters.computeIfAbsent(safeClient, k -> Counter.builder("gateway.ratelimit.rejected")
                .description("Number of requests rejected due to rate limiting or quota exhaustion")
                .tag("clientId", safeClient)
                .register(meterRegistry)
        ).increment();
    }

    private void registerCircuitBreakerGauge(CircuitBreaker cb) {
        String name = cb.getName();
        AtomicInteger stateGauge = circuitBreakerGauges.computeIfAbsent(name, k -> {
            AtomicInteger gaugeVal = new AtomicInteger(stateToInt(cb.getState()));
            meterRegistry.gauge("gateway.circuitbreaker.state",
                    io.micrometer.core.instrument.Tags.of("route", name),
                    gaugeVal,
                    AtomicInteger::get);
            return gaugeVal;
        });

        cb.getEventPublisher().onStateTransition(event -> {
            int stateVal = stateToInt(event.getStateTransition().getToState());
            stateGauge.set(stateVal);
            log.info("CircuitBreaker '{}' transitioned to state {}: {}", name, stateVal, event.getStateTransition());
        });
    }

    private int stateToInt(CircuitBreaker.State state) {
        return switch (state) {
            case CLOSED -> 0;
            case HALF_OPEN -> 1;
            case OPEN, FORCED_OPEN -> 2;
            case DISABLED, METRICS_ONLY -> -1;
        };
    }
}
