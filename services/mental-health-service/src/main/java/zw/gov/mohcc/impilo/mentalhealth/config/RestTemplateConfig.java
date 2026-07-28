package zw.gov.mohcc.impilo.mentalhealth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/** Vanilla {@link RestTemplate} for mental-health's outbound service-to-service calls (pct). */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
