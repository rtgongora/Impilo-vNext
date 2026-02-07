package zw.gov.mohcc.impilo.pct.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for the {@link RestTemplate} used by PCT integration services.
 *
 * <p>Provides a vanilla RestTemplate bean that is injected into all integration
 * services (BUTANO, OROS, MUSHEX, TUSO, UBOMI, VITO) for HTTP communication
 * with external Impilo services.</p>
 *
 * <p>For production use, this can be extended with connection timeouts,
 * interceptors for trust header forwarding, and retry policies.</p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
