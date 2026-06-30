package zw.gov.mohcc.impilo.inpatient.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Provides the {@link RestTemplate} used by inpatient integration clients (inventory/Dura consumption,
 * OROS orders). Short timeouts so a downstream outage degrades gracefully rather than blocking ward work.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate inpatientRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
