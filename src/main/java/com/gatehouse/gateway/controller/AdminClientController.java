package com.gatehouse.gateway.controller;

import com.gatehouse.gateway.domain.model.ApiClient;
import com.gatehouse.gateway.domain.model.ClientPlan;
import com.gatehouse.gateway.domain.model.ClientStatus;
import com.gatehouse.gateway.repository.ApiClientRepository;
import com.gatehouse.gateway.security.ApiKeyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/admin/clients")
public class AdminClientController {

    private final ApiClientRepository clientRepository;
    private final ApiKeyService apiKeyService;

    public AdminClientController(ApiClientRepository clientRepository, ApiKeyService apiKeyService) {
        this.clientRepository = clientRepository;
        this.apiKeyService = apiKeyService;
    }

    public static class CreateClientRequest {
        @NotBlank
        private String name;
        @NotBlank
        @Email
        private String ownerEmail;
        @NotNull
        private ClientPlan plan = ClientPlan.FREE;
        private Integer rateLimitPerMin;
        private Long quotaPerDay;

        public CreateClientRequest() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getOwnerEmail() { return ownerEmail; }
        public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
        public ClientPlan getPlan() { return plan; }
        public void setPlan(ClientPlan plan) { this.plan = plan; }
        public Integer getRateLimitPerMin() { return rateLimitPerMin; }
        public void setRateLimitPerMin(Integer rateLimitPerMin) { this.rateLimitPerMin = rateLimitPerMin; }
        public Long getQuotaPerDay() { return quotaPerDay; }
        public void setQuotaPerDay(Long quotaPerDay) { this.quotaPerDay = quotaPerDay; }
    }

    public static class ClientResponse {
        private String id;
        private String name;
        private String ownerEmail;
        private ClientPlan plan;
        private Integer rateLimitPerMin;
        private Long quotaPerDay;
        private ClientStatus status;
        private String apiKeyPrefix;
        private String rawApiKey;
        private Instant createdAt;
        private Instant updatedAt;

        public ClientResponse() {}

        public ClientResponse(String id, String name, String ownerEmail, ClientPlan plan,
                              Integer rateLimitPerMin, Long quotaPerDay, ClientStatus status,
                              String apiKeyPrefix, String rawApiKey, Instant createdAt, Instant updatedAt) {
            this.id = id;
            this.name = name;
            this.ownerEmail = ownerEmail;
            this.plan = plan;
            this.rateLimitPerMin = rateLimitPerMin;
            this.quotaPerDay = quotaPerDay;
            this.status = status;
            this.apiKeyPrefix = apiKeyPrefix;
            this.rawApiKey = rawApiKey;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String id;
            private String name;
            private String ownerEmail;
            private ClientPlan plan;
            private Integer rateLimitPerMin;
            private Long quotaPerDay;
            private ClientStatus status;
            private String apiKeyPrefix;
            private String rawApiKey;
            private Instant createdAt;
            private Instant updatedAt;

            public Builder id(String id) { this.id = id; return this; }
            public Builder name(String name) { this.name = name; return this; }
            public Builder ownerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; return this; }
            public Builder plan(ClientPlan plan) { this.plan = plan; return this; }
            public Builder rateLimitPerMin(Integer rateLimitPerMin) { this.rateLimitPerMin = rateLimitPerMin; return this; }
            public Builder quotaPerDay(Long quotaPerDay) { this.quotaPerDay = quotaPerDay; return this; }
            public Builder status(ClientStatus status) { this.status = status; return this; }
            public Builder apiKeyPrefix(String apiKeyPrefix) { this.apiKeyPrefix = apiKeyPrefix; return this; }
            public Builder rawApiKey(String rawApiKey) { this.rawApiKey = rawApiKey; return this; }
            public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
            public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

            public ClientResponse build() {
                return new ClientResponse(id, name, ownerEmail, plan, rateLimitPerMin, quotaPerDay,
                        status, apiKeyPrefix, rawApiKey, createdAt, updatedAt);
            }
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
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
        public String getApiKeyPrefix() { return apiKeyPrefix; }
        public void setApiKeyPrefix(String apiKeyPrefix) { this.apiKeyPrefix = apiKeyPrefix; }
        public String getRawApiKey() { return rawApiKey; }
        public void setRawApiKey(String rawApiKey) { this.rawApiKey = rawApiKey; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ClientResponse> createClient(@Valid @RequestBody CreateClientRequest req) {
        ApiKeyService.GeneratedApiKey genKey = apiKeyService.generateApiKey();
        String clientId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        int rateLimit = req.getRateLimitPerMin() != null ? req.getRateLimitPerMin() : req.getPlan().getDefaultRateLimitPerMin();
        long quota = req.getQuotaPerDay() != null ? req.getQuotaPerDay() : req.getPlan().getDefaultQuotaPerDay();

        ApiClient client = ApiClient.builder()
                .id(clientId)
                .name(req.getName())
                .apiKeyHash(genKey.keyHash())
                .apiKeyPrefix(genKey.prefix())
                .apiSecretHash(apiKeyService.sha256Hex(UUID.randomUUID().toString()))
                .ownerEmail(req.getOwnerEmail())
                .plan(req.getPlan())
                .rateLimitPerMin(rateLimit)
                .quotaPerDay(quota)
                .status(ClientStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return clientRepository.save(client)
                .map(saved -> ClientResponse.builder()
                        .id(saved.getId())
                        .name(saved.getName())
                        .ownerEmail(saved.getOwnerEmail())
                        .plan(saved.getPlan())
                        .rateLimitPerMin(saved.getRateLimitPerMin())
                        .quotaPerDay(saved.getQuotaPerDay())
                        .status(saved.getStatus())
                        .apiKeyPrefix(saved.getApiKeyPrefix())
                        .rawApiKey(genKey.rawApiKey())
                        .createdAt(saved.getCreatedAt())
                        .updatedAt(saved.getUpdatedAt())
                        .build()
                );
    }

    @GetMapping
    public Flux<ClientResponse> listClients() {
        return clientRepository.findAll()
                .map(this::toResponse);
    }

    @GetMapping("/{id}")
    public Mono<ClientResponse> getClient(@PathVariable String id) {
        return clientRepository.findById(id)
                .map(this::toResponse)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")));
    }

    @PostMapping("/{id}/rotate-key")
    public Mono<ClientResponse> rotateKey(@PathVariable String id) {
        return clientRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")))
                .flatMap(client -> {
                    String oldHash = client.getApiKeyHash();
                    ApiKeyService.GeneratedApiKey newKey = apiKeyService.generateApiKey();

                    client.setApiKeyHash(newKey.keyHash());
                    client.setApiKeyPrefix(newKey.prefix());
                    client.setUpdatedAt(Instant.now());

                    return apiKeyService.invalidateCache(oldHash)
                            .then(clientRepository.save(client))
                            .map(saved -> {
                                ClientResponse resp = toResponse(saved);
                                resp.setRawApiKey(newKey.rawApiKey());
                                return resp;
                            });
                });
    }

    private ClientResponse toResponse(ApiClient client) {
        return ClientResponse.builder()
                .id(client.getId())
                .name(client.getName())
                .ownerEmail(client.getOwnerEmail())
                .plan(client.getPlan())
                .rateLimitPerMin(client.getRateLimitPerMin())
                .quotaPerDay(client.getQuotaPerDay())
                .status(client.getStatus())
                .apiKeyPrefix(client.getApiKeyPrefix())
                .createdAt(client.getCreatedAt())
                .updatedAt(client.getUpdatedAt())
                .build();
    }
}
