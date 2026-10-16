package com.gatehouse.gateway.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "gatehouse.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Value("${gatehouse.flyway.url:jdbc:postgresql://localhost:5432/gatehouse_db}")
    private String url;

    @Value("${gatehouse.flyway.user:gatehouse}")
    private String user;

    @Value("${gatehouse.flyway.password:gatehouse_secret}")
    private String password;

    @Value("${gatehouse.flyway.locations:classpath:db/migration}")
    private String locations;

    @Value("${gatehouse.flyway.baseline-on-migrate:true}")
    private boolean baselineOnMigrate;

    @PostConstruct
    public void migrate() {
        log.info("Executing Flyway migration on database: {}", url);
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(url, user, password)
                    .locations(locations)
                    .baselineOnMigrate(baselineOnMigrate)
                    .load();
            var result = flyway.migrate();
            log.info("Flyway migration completed successfully. Applied {} migrations.", result.migrationsExecuted);
        } catch (Exception ex) {
            log.warn("Flyway migration skipped or failed (if running without Postgres, e.g. tests): {}", ex.getMessage());
        }
    }
}
