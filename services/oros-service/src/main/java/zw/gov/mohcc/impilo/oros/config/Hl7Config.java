package zw.gov.mohcc.impilo.oros.config;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared HL7 v2 context for the ORM-inbound / ORU-outbound adapters. The context itself is always
 * available (cheap); the MLLP listener/sender are separately flag-gated.
 */
@Configuration
public class Hl7Config {

    @Bean(destroyMethod = "close")
    public HapiContext hapiContext() {
        return new DefaultHapiContext();
    }
}
