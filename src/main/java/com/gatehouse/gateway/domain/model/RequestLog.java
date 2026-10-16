package com.gatehouse.gateway.domain.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("request_logs")
public class RequestLog {

    @Id
    private String id;

    @Column("client_id")
    private String clientId;

    @Column("route_id")
    private String routeId;

    @Column("method")
    private String method;

    @Column("path")
    private String path;

    @Column("status_code")
    private Integer statusCode;

    @Column("latency_ms")
    private Long latencyMs;

    @Column("request_bytes")
    private Long requestBytes;

    @Column("response_bytes")
    private Long responseBytes;

    @Column("timestamp")
    private Instant timestamp;

    public RequestLog() {}

    public RequestLog(String id, String clientId, String routeId, String method, String path,
                      Integer statusCode, Long latencyMs, Long requestBytes, Long responseBytes,
                      Instant timestamp) {
        this.id = id;
        this.clientId = clientId;
        this.routeId = routeId;
        this.method = method;
        this.path = path;
        this.statusCode = statusCode;
        this.latencyMs = latencyMs;
        this.requestBytes = requestBytes;
        this.responseBytes = responseBytes;
        this.timestamp = timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String clientId;
        private String routeId;
        private String method;
        private String path;
        private Integer statusCode;
        private Long latencyMs;
        private Long requestBytes;
        private Long responseBytes;
        private Instant timestamp;

        public Builder id(String id) { this.id = id; return this; }
        public Builder clientId(String clientId) { this.clientId = clientId; return this; }
        public Builder routeId(String routeId) { this.routeId = routeId; return this; }
        public Builder method(String method) { this.method = method; return this; }
        public Builder path(String path) { this.path = path; return this; }
        public Builder statusCode(Integer statusCode) { this.statusCode = statusCode; return this; }
        public Builder latencyMs(Long latencyMs) { this.latencyMs = latencyMs; return this; }
        public Builder requestBytes(Long requestBytes) { this.requestBytes = requestBytes; return this; }
        public Builder responseBytes(Long responseBytes) { this.responseBytes = responseBytes; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

        public RequestLog build() {
            return new RequestLog(id, clientId, routeId, method, path, statusCode, latencyMs, requestBytes, responseBytes, timestamp);
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public Long getRequestBytes() { return requestBytes; }
    public void setRequestBytes(Long requestBytes) { this.requestBytes = requestBytes; }
    public Long getResponseBytes() { return responseBytes; }
    public void setResponseBytes(Long responseBytes) { this.responseBytes = responseBytes; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
