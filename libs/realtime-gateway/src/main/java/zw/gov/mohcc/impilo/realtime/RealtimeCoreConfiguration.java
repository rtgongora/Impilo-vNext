package zw.gov.mohcc.impilo.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the transport-agnostic hub + instance beans. Owning services {@code @Import} this
 * configuration; the lib is not a Boot auto-configuration so it never starts without intent.
 */
@Configuration
public class RealtimeCoreConfiguration {

    @Bean
    public RealtimeInstance realtimeInstance() {
        return new RealtimeInstance();
    }

    @Bean
    public RealtimeHub realtimeHub() {
        return new RealtimeHub();
    }
}
