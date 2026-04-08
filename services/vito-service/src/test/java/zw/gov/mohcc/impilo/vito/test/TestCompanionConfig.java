package zw.gov.mohcc.impilo.vito.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.InMemoryIdempotencyRepository;

/**
 * Test configuration that overrides the companion library's JdbcIdempotencyRepository.
 *
 * In H2 test environments, the JdbcIdempotencyRepository's PostgreSQL-specific
 * ON CONFLICT syntax fails. This config supplies the fallback InMemoryRepository instead.
 */
@TestConfiguration
public class TestCompanionConfig {

    @Bean
    @Primary
    public IdempotencyRepository testIdempotencyRepository() {
        return new InMemoryIdempotencyRepository();
    }
}
