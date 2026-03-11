package zw.gov.mohcc.impilo.experience.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyService;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

@Configuration
public class IdempotencyConfig {

    @Bean
    public JdbcIdempotencyRepository idempotencyRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcIdempotencyRepository(jdbcTemplate, "experience_bff_idempotency", 24);
    }

    @Bean
    public IdempotencyService idempotencyService(JdbcIdempotencyRepository repository) {
        return new IdempotencyService(repository);
    }
}
