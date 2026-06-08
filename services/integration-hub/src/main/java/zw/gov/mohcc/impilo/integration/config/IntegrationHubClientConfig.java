package zw.gov.mohcc.impilo.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class IntegrationHubClientConfig {

    @Bean
    RestTemplate integrationHubRestTemplate() {
        return new RestTemplate();
    }
}
