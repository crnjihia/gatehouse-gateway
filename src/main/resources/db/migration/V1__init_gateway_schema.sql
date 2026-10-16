-- Flyway migration V1: Initial schema for Gatehouse Gateway

-- 1. ApiClient table
CREATE TABLE IF NOT EXISTS api_clients (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    api_key_hash VARCHAR(64) NOT NULL UNIQUE,
    api_key_prefix VARCHAR(16) NOT NULL,
    api_secret_hash VARCHAR(128) NOT NULL,
    owner_email VARCHAR(255) NOT NULL,
    plan VARCHAR(20) NOT NULL CHECK (plan IN ('FREE', 'PRO', 'ENTERPRISE')),
    rate_limit_per_min INT NOT NULL DEFAULT 60,
    quota_per_day BIGINT NOT NULL DEFAULT 1000,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_api_clients_key_hash ON api_clients(api_key_hash);
CREATE INDEX IF NOT EXISTS idx_api_clients_status ON api_clients(status);

-- 2. RouteConfig table
CREATE TABLE IF NOT EXISTS route_configs (
    id VARCHAR(36) PRIMARY KEY,
    route_id VARCHAR(100) NOT NULL UNIQUE,
    uri VARCHAR(500) NOT NULL,
    predicates JSONB NOT NULL DEFAULT '[]'::jsonb,
    filters JSONB NOT NULL DEFAULT '[]'::jsonb,
    route_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_route_configs_enabled ON route_configs(enabled);

-- 3. RequestLog table partitioned by month
CREATE TABLE IF NOT EXISTS request_logs (
    id VARCHAR(36) NOT NULL,
    client_id VARCHAR(36),
    route_id VARCHAR(100),
    method VARCHAR(10) NOT NULL,
    path VARCHAR(1000) NOT NULL,
    status_code INT NOT NULL,
    latency_ms BIGINT NOT NULL,
    request_bytes BIGINT NOT NULL DEFAULT 0,
    response_bytes BIGINT NOT NULL DEFAULT 0,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, timestamp)
) PARTITION BY RANGE (timestamp);

CREATE INDEX IF NOT EXISTS idx_request_logs_client_time ON request_logs(client_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_request_logs_route_time ON request_logs(route_id, timestamp);

-- Monthly partitions for 2026 and 2027
CREATE TABLE IF NOT EXISTS request_logs_2026_09 PARTITION OF request_logs
    FOR VALUES FROM ('2026-09-01 00:00:00+00') TO ('2026-10-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS request_logs_2026_10 PARTITION OF request_logs
    FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS request_logs_2026_11 PARTITION OF request_logs
    FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS request_logs_2026_12 PARTITION OF request_logs
    FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS request_logs_2027_01 PARTITION OF request_logs
    FOR VALUES FROM ('2027-01-01 00:00:00+00') TO ('2027-02-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS request_logs_default PARTITION OF request_logs DEFAULT;

-- Initial Seed Data: Default Routes & Demo Client
INSERT INTO route_configs (id, route_id, uri, predicates, filters, route_order, enabled, description)
VALUES 
(
    '00000000-0000-0000-0000-000000000001',
    'users-service',
    'http://wiremock:8080',
    '[{"name": "Path", "args": {"pattern": "/api/v1/users/**"}}]',
    '[{"name": "StripPrefix", "args": {"parts": "2"}}, {"name": "CircuitBreaker", "args": {"name": "usersCircuitBreaker", "fallbackUri": "forward:/fallback"}}, {"name": "TokenBucketRateLimit", "args": {}}, {"name": "RequestResponseTransformation", "args": {"maskFields": "$.password,$.ssn"}}]',
    1,
    TRUE,
    'Users downstream service with circuit breaker, token bucket rate limiter, and body masking'
),
(
    '00000000-0000-0000-0000-000000000002',
    'orders-service',
    'http://wiremock:8080',
    '[{"name": "Path", "args": {"pattern": "/api/v1/orders/**"}}, {"name": "Method", "args": {"methods": "GET,POST,PUT,DELETE"}}]',
    '[{"name": "StripPrefix", "args": {"parts": "2"}}, {"name": "CircuitBreaker", "args": {"name": "ordersCircuitBreaker", "fallbackUri": "forward:/fallback"}}, {"name": "TokenBucketRateLimit", "args": {}}, {"name": "Retry", "args": {"retries": 3, "series": "SERVER_ERROR", "methods": "GET,PUT", "backoff": {"firstBackoff": "100ms", "maxBackoff": "400ms", "factor": 2}}}]',
    2,
    TRUE,
    'Orders downstream service with retry on idempotent methods and circuit breaker'
),
(
    '00000000-0000-0000-0000-000000000003',
    'payments-service',
    'http://wiremock:8080',
    '[{"name": "Path", "args": {"pattern": "/api/v1/payments/**"}}]',
    '[{"name": "StripPrefix", "args": {"parts": "2"}}, {"name": "CircuitBreaker", "args": {"name": "paymentsCircuitBreaker", "fallbackUri": "forward:/fallback"}}, {"name": "TokenBucketRateLimit", "args": {}}, {"name": "RequestResponseTransformation", "args": {"maskFields": "$.cardNumber,$.cvv"}}]',
    3,
    TRUE,
    'Payments downstream service with PCI sensitive card masking and rate limiting'
)
ON CONFLICT (route_id) DO NOTHING;

-- Seed Demo ApiClient (API Key: gh_live_demo_enterprise_key_12345)
-- SHA-256 for 'gh_live_demo_enterprise_key_12345' is:
-- 6b97dbce6a0ca9762193b2a549ea5b8719bc42bb166b26cf8d4d9894e666a7b7
INSERT INTO api_clients (
    id, name, api_key_hash, api_key_prefix, api_secret_hash, owner_email, plan, rate_limit_per_min, quota_per_day, status
) VALUES (
    '10000000-0000-0000-0000-000000000001',
    'Acme Enterprise Client',
    '6b97dbce6a0ca9762193b2a549ea5b8719bc42bb166b26cf8d4d9894e666a7b7',
    'gh_live_demo',
    'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    'admin@acme.corp',
    'ENTERPRISE',
    6000,
    1000000,
    'ACTIVE'
)
ON CONFLICT (api_key_hash) DO NOTHING;
