package zw.gov.mohcc.impilo.tshepo.identity.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate beans for outbound service-to-service calls.
 *
 * <p>Separate beans for VITO (identity resolution) and keys-service (Ed25519 signing)
 * so each can have independent timeout and retry configuration.</p>
 */
@Configuration
public class RestClientConfig {

    @Bean(name = "vitoRestTemplate")
    public RestTemplate vitoRestTemplate(RestTemplateBuilder builder,
                                          IdentityProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return builder.rootUri(properties.vitoServiceUrl()).requestFactory(() -> factory).build();
    }

    @Bean(name = "keysRestTemplate")
    public RestTemplate keysRestTemplate(RestTemplateBuilder builder,
                                          IdentityProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return builder.rootUri(properties.keysServiceUrl()).requestFactory(() -> factory).build();
    }
}
