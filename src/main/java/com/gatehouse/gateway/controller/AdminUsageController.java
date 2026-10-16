package com.gatehouse.gateway.controller;

import com.gatehouse.gateway.domain.model.RequestLog;
import com.gatehouse.gateway.repository.RequestLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/admin/usage")
public class AdminUsageController {

    private static final Logger log = LoggerFactory.getLogger(AdminUsageController.class);

    private final RequestLogRepository requestLogRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminUsageController(RequestLogRepository requestLogRepository,
                                @org.springframework.beans.factory.annotation.Autowired(required = false)
                                ReactiveRedisTemplate<String, String> redisTemplate) {
        this.requestLogRepository = requestLogRepository;
        this.redisTemplate = redisTemplate;
    }

    public static class ClientUsageReport {
        private String clientId;
        private Instant from;
        private Instant to;
        private long totalRequests;
        private double averageLatencyMs;
        private long liveMinuteRequests;
        private long liveDailyQuotaUsed;
        private List<RequestLog> recentLogs;

        public ClientUsageReport() {}

        public ClientUsageReport(String clientId, Instant from, Instant to, long totalRequests,
                                 double averageLatencyMs, long liveMinuteRequests,
                                 long liveDailyQuotaUsed, List<RequestLog> recentLogs) {
            this.clientId = clientId;
            this.from = from;
            this.to = to;
            this.totalRequests = totalRequests;
            this.averageLatencyMs = averageLatencyMs;
            this.liveMinuteRequests = liveMinuteRequests;
            this.liveDailyQuotaUsed = liveDailyQuotaUsed;
            this.recentLogs = recentLogs;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String clientId;
            private Instant from;
            private Instant to;
            private long totalRequests;
            private double averageLatencyMs;
            private long liveMinuteRequests;
            private long liveDailyQuotaUsed;
            private List<RequestLog> recentLogs;

            public Builder clientId(String clientId) { this.clientId = clientId; return this; }
            public Builder from(Instant from) { this.from = from; return this; }
            public Builder to(Instant to) { this.to = to; return this; }
            public Builder totalRequests(long totalRequests) { this.totalRequests = totalRequests; return this; }
            public Builder averageLatencyMs(double averageLatencyMs) { this.averageLatencyMs = averageLatencyMs; return this; }
            public Builder liveMinuteRequests(long liveMinuteRequests) { this.liveMinuteRequests = liveMinuteRequests; return this; }
            public Builder liveDailyQuotaUsed(long liveDailyQuotaUsed) { this.liveDailyQuotaUsed = liveDailyQuotaUsed; return this; }
            public Builder recentLogs(List<RequestLog> recentLogs) { this.recentLogs = recentLogs; return this; }

            public ClientUsageReport build() {
                return new ClientUsageReport(clientId, from, to, totalRequests, averageLatencyMs,
                        liveMinuteRequests, liveDailyQuotaUsed, recentLogs);
            }
        }

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public Instant getFrom() { return from; }
        public void setFrom(Instant from) { this.from = from; }
        public Instant getTo() { return to; }
        public void setTo(Instant to) { this.to = to; }
        public long getTotalRequests() { return totalRequests; }
        public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }
        public double getAverageLatencyMs() { return averageLatencyMs; }
        public void setAverageLatencyMs(double averageLatencyMs) { this.averageLatencyMs = averageLatencyMs; }
        public long getLiveMinuteRequests() { return liveMinuteRequests; }
        public void setLiveMinuteRequests(long liveMinuteRequests) { this.liveMinuteRequests = liveMinuteRequests; }
        public long getLiveDailyQuotaUsed() { return liveDailyQuotaUsed; }
        public void setLiveDailyQuotaUsed(long liveDailyQuotaUsed) { this.liveDailyQuotaUsed = liveDailyQuotaUsed; }
        public List<RequestLog> getRecentLogs() { return recentLogs; }
        public void setRecentLogs(List<RequestLog> recentLogs) { this.recentLogs = recentLogs; }
    }

    @GetMapping("/{clientId}")
    public Mono<ClientUsageReport> getUsage(
            @PathVariable String clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        Instant effectiveTo = (to != null) ? to : Instant.now();
        Instant effectiveFrom = (from != null) ? from : effectiveTo.minus(24, ChronoUnit.HOURS);

        long currentEpochMinute = Instant.now().getEpochSecond() / 60;
        String dateStr = LocalDate.now(ZoneOffset.UTC).toString();

        String rateKey = "rate:" + clientId + ":" + currentEpochMinute;
        String quotaKey = "quota:" + clientId + ":" + dateStr;

        Mono<Long> liveMinuteReqsMono = (redisTemplate != null)
                ? redisTemplate.opsForValue().get(rateKey)
                        .map(Long::parseLong)
                        .defaultIfEmpty(0L)
                : Mono.just(0L);

        Mono<Long> liveDailyQuotaMono = (redisTemplate != null)
                ? redisTemplate.opsForValue().get(quotaKey)
                        .map(Long::parseLong)
                        .defaultIfEmpty(0L)
                : Mono.just(0L);

        Mono<Long> totalReqsMono = requestLogRepository.countByClientIdAndTimestampBetween(clientId, effectiveFrom, effectiveTo)
                .defaultIfEmpty(0L);

        Mono<Double> avgLatencyMono = requestLogRepository.averageLatencyByClientIdAndTimestampBetween(clientId, effectiveFrom, effectiveTo)
                .defaultIfEmpty(0.0);

        Mono<List<RequestLog>> recentLogsMono = requestLogRepository
                .findByClientIdAndTimestampBetweenOrderByTimestampDesc(clientId, effectiveFrom, effectiveTo)
                .take(50)
                .collectList();

        return Mono.zip(totalReqsMono, avgLatencyMono, liveMinuteReqsMono, liveDailyQuotaMono, recentLogsMono)
                .map(tuple -> ClientUsageReport.builder()
                        .clientId(clientId)
                        .from(effectiveFrom)
                        .to(effectiveTo)
                        .totalRequests(tuple.getT1())
                        .averageLatencyMs(tuple.getT2() != null ? Math.round(tuple.getT2() * 100.0) / 100.0 : 0.0)
                        .liveMinuteRequests(tuple.getT3())
                        .liveDailyQuotaUsed(tuple.getT4())
                        .recentLogs(tuple.getT5())
                        .build()
                );
    }
}
