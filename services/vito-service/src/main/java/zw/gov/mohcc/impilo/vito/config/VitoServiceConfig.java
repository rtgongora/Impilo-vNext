package zw.gov.mohcc.impilo.vito.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.shared.crypto.Argon2IdService;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;

/**
 * Wire shared-core crypto services with VITO-specific parameters.
 * Architect directive: Argon2id m=64MB, t=3, p=4 for VITO.
 */
@Configuration
public class VitoServiceConfig {

    @Bean
    public Argon2IdService argon2IdService(VitoProperties props) {
        return new Argon2IdService(
                props.getArgon2().getMemoryCostKb(),
                props.getArgon2().getIterations(),
                props.getArgon2().getParallelism()
        );
    }

    @Bean
    public HmacService hmacService(VitoProperties props) {
        return new HmacService(props.getHmac().getPepper());
    }
}
