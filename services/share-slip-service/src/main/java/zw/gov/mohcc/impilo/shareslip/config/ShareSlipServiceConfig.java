package zw.gov.mohcc.impilo.shareslip.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;

/**
 * Bean definitions for share-slip-service.
 * Wires the HmacService from shared-core with the service-specific pepper.
 */
@Configuration
public class ShareSlipServiceConfig {

    @Bean
    public HmacService hmacService(ShareSlipProperties properties) {
        return new HmacService(properties.getHmac().getPepper());
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
