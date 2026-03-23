package zw.gov.mohcc.impilo.tshepo.authz.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * REST client configuration for inter-service calls
 * (consent-service, identity-service, audit-service, keys-service).
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return builder.requestFactory(() -> factory).build();
    }
}
