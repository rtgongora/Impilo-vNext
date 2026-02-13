package zw.gov.mohcc.impilo.vito.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

/**
 * VITO-specific Tech Companion configuration.
 *
 * Only provides the schema-prefixed idempotency repository.
 * Uses the existing vito.idempotency_keys table (V016 migration).
 * All filter registrations, exception handler, and service wiring
 * are handled by TechCompanionAutoConfiguration.
 */
@Configuration
public class CompanionV11Config {

    @Bean
    public IdempotencyRepository companionIdempotencyRepository(JdbcTemplate jdbc) {
        return new JdbcIdempotencyRepository(jdbc, "vito.idempotency_keys", 24);
    }
}
