package zw.gov.mohcc.impilo.mushex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.mushex.service.HmacService;

/**
 * Wires MUSHEX-local HMAC utilities (webhook verification, token hashing).
 */
@Configuration
public class MushexCryptoConfig {

    @Bean
    public HmacService hmacService(MushexProperties properties) {
        return new HmacService(properties.getHmacPepper());
    }
}
