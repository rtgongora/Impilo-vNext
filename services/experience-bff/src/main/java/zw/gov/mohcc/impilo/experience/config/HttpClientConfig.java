package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/**
 * HTTP client configuration for sovereign service calls.
 *
 * <p>Configures RestTemplate with:</p>
 * <ul>
 *   <li>Connection timeout: 3 seconds (fail fast if service unreachable)</li>
 *   <li>Read timeout: 5 seconds (fail fast if service is slow)</li>
 *   <li>Trust header forwarding interceptor</li>
 * </ul>
 *
 * <p>With Java 21 virtual threads enabled, blocking RestTemplate calls
 * are efficient — each virtual thread costs ~1KB vs ~1MB for platform
 * threads. No need for reactive WebClient.</p>
 */
@Configuration
public class HttpClientConfig {

    @Bean
    @Primary
    public RestTemplate serviceRestTemplate(
            ClientHttpRequestInterceptor trustHeaderForwardingInterceptor) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));

        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setInterceptors(List.of(trustHeaderForwardingInterceptor));

        return restTemplate;
    }
}
