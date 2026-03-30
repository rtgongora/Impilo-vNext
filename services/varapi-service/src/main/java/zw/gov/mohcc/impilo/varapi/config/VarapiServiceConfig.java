package zw.gov.mohcc.impilo.varapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.crypto.Argon2IdService;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;

@Configuration
public class VarapiServiceConfig {

    @Bean
    public Argon2IdService argon2IdService(VarapiProperties props) {
        return new Argon2IdService(
                props.getArgon2().getMemoryCostKb(),
                props.getArgon2().getIterations(),
                props.getArgon2().getParallelism()
        );
    }

    @Bean
    public HmacService hmacService(VarapiProperties props) {
        return new HmacService(props.getHmac().getPepper());
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ca.uhn.fhir.context.FhirContext fhirContext() {
        return ca.uhn.fhir.context.FhirContext.forR4();
    }
}
