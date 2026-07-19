package zw.gov.mohcc.impilo.shared.biometric;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures the shared {@link BiometricVerificationClient} in every service
 * that depends on shared-core, so the biometric-verification seam is available by
 * injection everywhere without per-service wiring. A service can override by
 * declaring its own {@code BiometricVerificationClient} bean.
 */
@AutoConfiguration
public class BiometricAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BiometricVerificationClient biometricVerificationClient(
            @Value("${impilo.biometric.enabled:true}") boolean enabled,
            @Value("${ABIS_BASE_URL:${impilo.biometric.abis-base-url:http://localhost:8186}}") String abisBaseUrl) {
        return new BiometricVerificationClient(enabled, abisBaseUrl);
    }
}
