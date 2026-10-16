package com.gatehouse.gateway.domain.model;

public enum ClientPlan {
    FREE(60, 1_000L),
    PRO(600, 100_000L),
    ENTERPRISE(6000, 1_000_000L);

    private final int defaultRateLimitPerMin;
    private final long defaultQuotaPerDay;

    ClientPlan(int defaultRateLimitPerMin, long defaultQuotaPerDay) {
        this.defaultRateLimitPerMin = defaultRateLimitPerMin;
        this.defaultQuotaPerDay = defaultQuotaPerDay;
    }

    public int getDefaultRateLimitPerMin() {
        return defaultRateLimitPerMin;
    }

    public long getDefaultQuotaPerDay() {
        return defaultQuotaPerDay;
    }
}
