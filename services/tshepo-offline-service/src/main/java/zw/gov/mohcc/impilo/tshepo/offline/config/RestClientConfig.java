package zw.gov.mohcc.impilo.tshepo.offline.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configures RestClient instances for inter-service communication
 * with TSHEPO sub-services (identity, keys, authz).
 */
@Configuration
public class RestClientConfig {

    @Value("${tshepo.services.identity-url}")
    private String identityBaseUrl;

    @Value("${tshepo.services.keys-url}")
    private String keysBaseUrl;

    @Value("${tshepo.services.authz-url}")
    private String authzBaseUrl;

    @Bean
    public RestClient identityRestClient() {
        return RestClient.builder()
                .baseUrl(identityBaseUrl)
                .build();
    }

    @Bean
    public RestClient keysRestClient() {
        return RestClient.builder()
                .baseUrl(keysBaseUrl)
                .build();
    }

    @Bean
    public RestClient authzRestClient() {
        return RestClient.builder()
                .baseUrl(authzBaseUrl)
                .build();
    }
}
