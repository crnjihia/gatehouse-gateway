package com.gatehouse.gateway.domain.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("api_clients")
public class ApiClient {

    @Id
    private String id;

    @Column("name")
    private String name;

    @Column("api_key_hash")
    private String apiKeyHash;

    @Column("api_key_prefix")
    private String apiKeyPrefix;

    @Column("api_secret_hash")
    private String apiSecretHash;

    @Column("owner_email")
    private String ownerEmail;

    @Column("plan")
    private ClientPlan plan;

    @Column("rate_limit_per_min")
    private Integer rateLimitPerMin;

    @Column("quota_per_day")
    private Long quotaPerDay;

    @Column("status")
    private ClientStatus status;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    public ApiClient() {}

    public ApiClient(String id, String name, String apiKeyHash, String apiKeyPrefix,
                     String apiSecretHash, String ownerEmail, ClientPlan plan,
                     Integer rateLimitPerMin, Long quotaPerDay, ClientStatus status,
                     Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.apiKeyHash = apiKeyHash;
        this.apiKeyPrefix = apiKeyPrefix;
        this.apiSecretHash = apiSecretHash;
        this.ownerEmail = ownerEmail;
        this.plan = plan;
        this.rateLimitPerMin = rateLimitPerMin;
        this.quotaPerDay = quotaPerDay;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String apiKeyHash;
        private String apiKeyPrefix;
        private String apiSecretHash;
        private String ownerEmail;
        private ClientPlan plan;
        private Integer rateLimitPerMin;
        private Long quotaPerDay;
        private ClientStatus status;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder apiKeyHash(String apiKeyHash) { this.apiKeyHash = apiKeyHash; return this; }
        public Builder apiKeyPrefix(String apiKeyPrefix) { this.apiKeyPrefix = apiKeyPrefix; return this; }
        public Builder apiSecretHash(String apiSecretHash) { this.apiSecretHash = apiSecretHash; return this; }
        public Builder ownerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; return this; }
        public Builder plan(ClientPlan plan) { this.plan = plan; return this; }
        public Builder rateLimitPerMin(Integer rateLimitPerMin) { this.rateLimitPerMin = rateLimitPerMin; return this; }
        public Builder quotaPerDay(Long quotaPerDay) { this.quotaPerDay = quotaPerDay; return this; }
        public Builder status(ClientStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public ApiClient build() {
            return new ApiClient(id, name, apiKeyHash, apiKeyPrefix, apiSecretHash,
                    ownerEmail, plan, rateLimitPerMin, quotaPerDay, status, createdAt, updatedAt);
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getApiKeyHash() { return apiKeyHash; }
    public void setApiKeyHash(String apiKeyHash) { this.apiKeyHash = apiKeyHash; }
    public String getApiKeyPrefix() { return apiKeyPrefix; }
    public void setApiKeyPrefix(String apiKeyPrefix) { this.apiKeyPrefix = apiKeyPrefix; }
    public String getApiSecretHash() { return apiSecretHash; }
    public void setApiSecretHash(String apiSecretHash) { this.apiSecretHash = apiSecretHash; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
    public ClientPlan getPlan() { return plan; }
    public void setPlan(ClientPlan plan) { this.plan = plan; }
    public Integer getRateLimitPerMin() { return rateLimitPerMin; }
    public void setRateLimitPerMin(Integer rateLimitPerMin) { this.rateLimitPerMin = rateLimitPerMin; }
    public Long getQuotaPerDay() { return quotaPerDay; }
    public void setQuotaPerDay(Long quotaPerDay) { this.quotaPerDay = quotaPerDay; }
    public ClientStatus getStatus() { return status; }
    public void setStatus(ClientStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
