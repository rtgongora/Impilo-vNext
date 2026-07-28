package zw.gov.mohcc.impilo.surgery.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Provides the {@link RestTemplate} used by surgery-service integration clients (the pct-service
 * problem contribution). Short timeouts so a downstream outage degrades gracefully.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate surgeryRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
