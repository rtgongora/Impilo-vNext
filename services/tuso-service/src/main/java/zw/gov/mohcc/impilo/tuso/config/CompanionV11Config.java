package zw.gov.mohcc.impilo.tuso.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

/**
 * TUSO-specific Tech Companion configuration.
 *
 * Only provides the schema-prefixed idempotency repository.
 * All filter registrations, exception handler, and service wiring
 * are handled by TechCompanionAutoConfiguration.
 */
@Configuration
public class CompanionV11Config {

    @Bean
    public IdempotencyRepository companionIdempotencyRepository(JdbcTemplate jdbc) {
        return new JdbcIdempotencyRepository(jdbc, "tuso.idempotency_keys", 24);
    }
}
