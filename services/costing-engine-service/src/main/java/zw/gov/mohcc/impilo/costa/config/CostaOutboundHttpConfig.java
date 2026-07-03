package zw.gov.mohcc.impilo.costa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class CostaOutboundHttpConfig {

    @Bean(name = "mushexRestClient")
    RestClient mushexRestClient(RestClient.Builder builder, ImpiloIntegrationProperties properties) {
        return builder
                .baseUrl(properties.getMushexBaseUrl().replaceAll("/$", ""))
                .build();
    }

    @Bean(name = "coverageRestClient")
    RestClient coverageRestClient(RestClient.Builder builder, ImpiloIntegrationProperties properties) {
        return builder
                .baseUrl(properties.getCoverageBaseUrl().replaceAll("/$", ""))
                .build();
    }
}
