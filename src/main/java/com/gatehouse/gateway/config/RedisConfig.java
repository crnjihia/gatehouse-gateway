package com.gatehouse.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "gatehouse.rate-limiting", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {

    @Bean
    @Primary
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }

    /**
     * Lua script for atomic Token Bucket / Minute Window + Daily Quota rate limiting.
     * KEY[1]: rateKey (e.g. rate:{clientId}:{minute})
     * KEY[2]: quotaKey (e.g. quota:{clientId}:{date})
     * ARGV[1]: maxRatePerMin
     * ARGV[2]: maxQuotaPerDay
     * ARGV[3]: rateWindowTtlSeconds (60)
     * ARGV[4]: quotaWindowTtlSeconds (86400)
     *
     * Returns List<Long>:
     * [0] allowed: 1 = allowed, 0 = rate limit exceeded, -1 = quota exceeded
     * [1] rateRemaining
     * [2] quotaRemaining
     * [3] resetSeconds
     */
    @Bean
    public RedisScript<List> rateLimiterLuaScript() {
        String script = """
            local rateKey = KEYS[1]
            local quotaKey = KEYS[2]
            local maxRate = tonumber(ARGV[1])
            local maxQuota = tonumber(ARGV[2])
            local rateTtl = tonumber(ARGV[3])
            local quotaTtl = tonumber(ARGV[4])

            -- Check daily quota first
            local currentQuota = redis.call('GET', quotaKey)
            if currentQuota and tonumber(currentQuota) >= maxQuota then
                local ttl = redis.call('TTL', quotaKey)
                return {-1, 0, 0, ttl > 0 and ttl or quotaTtl}
            end

            -- Check minute rate limit
            local currentRate = redis.call('GET', rateKey)
            if currentRate and tonumber(currentRate) >= maxRate then
                local ttl = redis.call('TTL', rateKey)
                return {0, 0, maxQuota - (currentQuota and tonumber(currentQuota) or 0), ttl > 0 and ttl or rateTtl}
            end

            -- Increment minute rate counter
            local newRate = redis.call('INCR', rateKey)
            if newRate == 1 then
                redis.call('EXPIRE', rateKey, rateTtl)
            end

            -- Increment daily quota counter
            local newQuota = redis.call('INCR', quotaKey)
            if newQuota == 1 then
                redis.call('EXPIRE', quotaKey, quotaTtl)
            end

            local rateTtlLeft = redis.call('TTL', rateKey)
            local rateRemaining = math.max(0, maxRate - newRate)
            local quotaRemaining = math.max(0, maxQuota - newQuota)

            return {1, rateRemaining, quotaRemaining, rateTtlLeft > 0 and rateTtlLeft or rateTtl}
        """;
        return new DefaultRedisScript<>(script, List.class);
    }
}
