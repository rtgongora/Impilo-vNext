package zw.gov.mohcc.impilo.inventory.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate configuration for inter-service HTTP calls.
 *
 * <p>Provides a pre-configured {@link RestTemplate} bean with sensible
 * timeouts for calling downstream services such as the pharmacy service
 * and inventory-elmis-adapter.</p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }
}
