package zw.gov.mohcc.impilo.tshepo.consent.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HAPI FHIR configuration. Provides singleton FhirContext (expensive to create)
 * and a JSON parser bean for FHIR R4 Consent resource serialization.
 */
@Configuration
public class FhirConfig {

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public IParser fhirJsonParser(FhirContext fhirContext) {
        IParser parser = fhirContext.newJsonParser();
        parser.setPrettyPrint(false);
        parser.setStripVersionsFromReferences(true);
        return parser;
    }
}
