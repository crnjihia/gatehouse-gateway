package com.gatehouse.gateway.simulation;

import io.gatling.javaapi.core.CoreDsl;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpDsl;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Gatling Load Test Simulation for Gatehouse Gateway.
 * Target: 10,000 Requests Per Second (RPS)
 * Assertion: p99 Latency < 100ms and Successful Response Rate > 99%
 */
public class GatewayLoadSimulation extends Simulation {

    private static final String TARGET_BASE_URL = System.getProperty("gateway.url", "http://localhost:8080");
    private static final String DEMO_API_KEY = System.getProperty("gateway.apikey", "gh_live_demo_enterprise_key_12345");

    private final HttpProtocolBuilder httpProtocol = HttpDsl.http
            .baseUrl(TARGET_BASE_URL)
            .acceptHeader("application/json")
            .header("X-API-Key", DEMO_API_KEY)
            .shareConnections();

    private final ScenarioBuilder usersScenario = scenario("Gateway Enterprise Client Routing Scenario")
            .exec(
                    http("GET Users Downstream Request")
                            .get("/api/v1/users/1001")
                            .header("X-Request-Id", "#{session.userId}")
                            .check(status().in(200, 304))
            );

    private final ScenarioBuilder healthScenario = scenario("Gateway Health Check Scenario")
            .exec(
                    http("GET Actuator Health")
                            .get("/actuator/health")
                            .check(status().is(200))
            );

    {
        PopulationBuilder rampTo10kRps = usersScenario.injectOpen(
                rampUsersPerSec(500).to(10000).during(Duration.ofSeconds(30)),
                constantUsersPerSec(10000).during(Duration.ofSeconds(60))
        );

        PopulationBuilder healthCheckLoad = healthScenario.injectOpen(
                constantUsersPerSec(100).during(Duration.ofSeconds(90))
        );

        setUp(rampTo10kRps, healthCheckLoad)
                .protocols(httpProtocol)
                .assertions(
                        // Assert that 99th percentile response time is strictly under 100ms
                        global().responseTime().percentile3().lt(100),
                        // Assert that 95th percentile response time is under 50ms
                        global().responseTime().percentile2().lt(50),
                        // Assert successful responses > 99%
                        global().successfulRequests().percent().gt(99.0)
                );
    }
}
