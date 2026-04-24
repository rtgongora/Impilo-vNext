package zw.gov.mohcc.impilo.pharmacy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;

/**
 * Wires shared-core crypto beans for pharmacy workflows.
 */
@Configuration
public class PharmacyCryptoConfig {

    @Bean
    public HmacService hmacService(PharmacyProperties properties) {
        return new HmacService(properties.getHmac().getPepper());
    }
}
