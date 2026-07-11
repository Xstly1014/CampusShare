package com.campushare.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component("pgvector")
public class PgVectorHealthIndicator implements HealthIndicator {

    private final JdbcTemplate pgvectorJdbcTemplate;

    public PgVectorHealthIndicator(@Qualifier("pgvectorJdbcTemplate") JdbcTemplate pgvectorJdbcTemplate) {
        this.pgvectorJdbcTemplate = pgvectorJdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            Integer result = pgvectorJdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result != null && result == 1) {
                return Health.up().build();
            }
            return Health.down().withDetail("error", "Unexpected result from pgvector").build();
        } catch (Exception e) {
            log.warn("PgVector health check failed: {}", e.getMessage());
            return Health.down(e)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
