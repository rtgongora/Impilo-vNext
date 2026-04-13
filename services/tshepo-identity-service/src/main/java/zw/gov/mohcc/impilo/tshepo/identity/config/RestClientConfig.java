package zw.gov.mohcc.impilo.tshepo.identity.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

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
        return builder
                .rootUri(properties.vitoServiceUrl())
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Bean(name = "keysRestTemplate")
    public RestTemplate keysRestTemplate(RestTemplateBuilder builder,
                                          IdentityProperties properties) {
        return builder
                .rootUri(properties.keysServiceUrl())
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
