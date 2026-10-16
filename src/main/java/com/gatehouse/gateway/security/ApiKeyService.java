package com.gatehouse.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatehouse.gateway.domain.model.ApiClient;
import com.gatehouse.gateway.domain.model.ClientPlan;
import com.gatehouse.gateway.domain.model.ClientStatus;
import com.gatehouse.gateway.repository.ApiClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private static final String KEY_PREFIX = "gh_live_";
    private static final String CLIENT_CACHE_PREFIX = "cache:client:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    private final ApiClientRepository apiClientRepository;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ReactiveRedisTemplate<String, String> redisTemplate;

    public ApiKeyService(ApiClientRepository apiClientRepository, ObjectMapper objectMapper) {
        this.apiClientRepository = apiClientRepository;
        this.objectMapper = objectMapper;
    }

    public record GeneratedApiKey(String rawApiKey, String keyHash, String prefix) {}

    public GeneratedApiKey generateApiKey() {
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        String randomHex = HexFormat.of().formatHex(randomBytes);
        String rawKey = KEY_PREFIX + randomHex;
        String keyHash = sha256Hex(rawKey);
        String prefix = KEY_PREFIX + randomHex.substring(0, 4);
        return new GeneratedApiKey(rawKey, keyHash, prefix);
    }

    public String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public Mono<ApiClient> findByApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Mono.empty();
        }
        String keyHash = sha256Hex(rawApiKey.trim());
        if (redisTemplate == null) {
            return apiClientRepository.findByApiKeyHash(keyHash);
        }

        String cacheKey = CLIENT_CACHE_PREFIX + keyHash;

        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cachedJson -> {
                    try {
                        return Mono.just(objectMapper.readValue(cachedJson, ApiClient.class));
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to deserialize cached client: {}", e.getMessage());
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(
                        apiClientRepository.findByApiKeyHash(keyHash)
                                .flatMap(client -> {
                                    try {
                                        String json = objectMapper.writeValueAsString(client);
                                        return redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL)
                                                .thenReturn(client);
                                    } catch (JsonProcessingException e) {
                                        return Mono.just(client);
                                    }
                                })
                );
    }

    public Mono<Void> invalidateCache(String keyHash) {
        if (redisTemplate == null) {
            return Mono.empty();
        }
        return redisTemplate.delete(CLIENT_CACHE_PREFIX + keyHash).then();
    }
}
