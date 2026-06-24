package zw.gov.mohcc.impilo.inventory.elmis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP client for outbound calls to the external national eLMIS, with timeouts
 * derived from {@code elmis.connector}.
 */
@Configuration
public class ElmisClientConfig {

    @Bean
    public RestTemplate elmisRestTemplate(ElmisAdapterProperties properties) {
        Duration timeout = Duration.ofSeconds(properties.getConnector().getTimeoutSeconds());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return new RestTemplate(factory);
    }
}
