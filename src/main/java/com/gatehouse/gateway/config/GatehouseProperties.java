package com.gatehouse.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component("gatehouseProperties")
@ConfigurationProperties(prefix = "gatehouse")
public class GatehouseProperties {

    private Security security = new Security();
    private Routing routing = new Routing();
    private RateLimiting rateLimiting = new RateLimiting();

    public GatehouseProperties() {}

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Routing getRouting() {
        return routing;
    }

    public void setRouting(Routing routing) {
        this.routing = routing;
    }

    public RateLimiting getRateLimiting() {
        return rateLimiting;
    }

    public void setRateLimiting(RateLimiting rateLimiting) {
        this.rateLimiting = rateLimiting;
    }

    public static class Security {
        private Jwt jwt = new Jwt();
        private Admin admin = new Admin();

        public Security() {}

        public Jwt getJwt() {
            return jwt;
        }

        public void setJwt(Jwt jwt) {
            this.jwt = jwt;
        }

        public Admin getAdmin() {
            return admin;
        }

        public void setAdmin(Admin admin) {
            this.admin = admin;
        }
    }

    public static class Jwt {
        private String issuer = "gatehouse-auth";
        private String audience = "gatehouse-api";
        private String publicKeyPem = "";

        public Jwt() {}

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getPublicKeyPem() {
            return publicKeyPem;
        }

        public void setPublicKeyPem(String publicKeyPem) {
            this.publicKeyPem = publicKeyPem;
        }
    }

    public static class Admin {
        private String secretToken = "gatehouse-master-admin-key-9988";

        public Admin() {}

        public String getSecretToken() {
            return secretToken;
        }

        public void setSecretToken(String secretToken) {
            this.secretToken = secretToken;
        }
    }

    public static class Routing {
        private int refreshIntervalSeconds = 30;

        public Routing() {}

        public int getRefreshIntervalSeconds() {
            return refreshIntervalSeconds;
        }

        public void setRefreshIntervalSeconds(int refreshIntervalSeconds) {
            this.refreshIntervalSeconds = refreshIntervalSeconds;
        }
    }

    public static class RateLimiting {
        private boolean enabled = true;
        private int freeTierRatePerMin = 60;
        private int proTierRatePerMin = 600;
        private int enterpriseTierRatePerMin = 6000;

        public RateLimiting() {}

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getFreeTierRatePerMin() {
            return freeTierRatePerMin;
        }

        public void setFreeTierRatePerMin(int freeTierRatePerMin) {
            this.freeTierRatePerMin = freeTierRatePerMin;
        }

        public int getProTierRatePerMin() {
            return proTierRatePerMin;
        }

        public void setProTierRatePerMin(int proTierRatePerMin) {
            this.proTierRatePerMin = proTierRatePerMin;
        }

        public int getEnterpriseTierRatePerMin() {
            return enterpriseTierRatePerMin;
        }

        public void setEnterpriseTierRatePerMin(int enterpriseTierRatePerMin) {
            this.enterpriseTierRatePerMin = enterpriseTierRatePerMin;
        }
    }
}
