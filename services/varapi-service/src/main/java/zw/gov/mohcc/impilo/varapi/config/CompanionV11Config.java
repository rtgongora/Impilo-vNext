package zw.gov.mohcc.impilo.varapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

/**
 * VARAPI-specific Tech Companion configuration.
 *
 * Only provides the schema-prefixed idempotency repository.
 * All filter registrations, exception handler, and service wiring
 * are handled by TechCompanionAutoConfiguration.
 */
@Configuration
public class CompanionV11Config {

    @Bean
    public IdempotencyRepository companionIdempotencyRepository(JdbcTemplate jdbc) {
        return new JdbcIdempotencyRepository(jdbc, "varapi.idempotency_keys", 24);
    }
}
