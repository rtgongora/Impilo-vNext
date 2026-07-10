package zw.gov.mohcc.impilo.ndila.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class NdilaOsmHttpConfiguration {

    @Bean
    RestClient ndilaOsmRestClient(
            @Value("${ndila.providers.osm.http-fetch-timeout-ms:8000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
