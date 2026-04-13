package zw.gov.mohcc.impilo.experience.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyService;
import zw.gov.mohcc.impilo.experience.idempotency.RedisIdempotencyRepository;

@Configuration
public class IdempotencyConfig {

    @Bean
    public IdempotencyRepository idempotencyRepository(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            @Value("${impilo.experience.idempotency.ttl-hours:24}") long ttlHours) {
        return new RedisIdempotencyRepository(stringRedisTemplate, objectMapper, ttlHours);
    }

    @Bean
    public IdempotencyService idempotencyService(IdempotencyRepository repository) {
        return new IdempotencyService(repository);
    }
}
