package zw.gov.mohcc.impilo.tshepo.authz.stepup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * Fail-closed default beans for the step-up integration seams. A real deployment
 * provides its own {@code TotpSecretProvider} (enrolment store), {@code OtpDeliveryAdapter}
 * (SMS provider) and {@code BiometricAssertionVerifier} (matcher); until then those
 * modes refuse to complete. TOTP and SUPERVISOR_APPROVAL do not depend on these.
 */
@Configuration
public class StepUpProvidersConfig {

    private static final Logger log = LoggerFactory.getLogger(StepUpProvidersConfig.class);

    @Bean
    @ConditionalOnMissingBean(StepUpProviders.TotpSecretProvider.class)
    public StepUpProviders.TotpSecretProvider unconfiguredTotpSecretProvider() {
        log.warn("Step-up TOTP secret provider not configured — TOTP step-up will fail closed (no enrolled secrets).");
        return (tenantId, actorId) -> Optional.empty();
    }

    @Bean
    @ConditionalOnMissingBean(StepUpProviders.OtpDeliveryAdapter.class)
    public StepUpProviders.OtpDeliveryAdapter unconfiguredOtpDeliveryAdapter() {
        log.warn("Step-up SMS OTP delivery not configured — SMS_OTP step-up will fail closed.");
        return (tenantId, actorId, otp) -> {
            throw new StepUpUnavailableException("SMS OTP delivery is not configured");
        };
    }

    @Bean
    @ConditionalOnMissingBean(StepUpProviders.BiometricAssertionVerifier.class)
    public StepUpProviders.BiometricAssertionVerifier unconfiguredBiometricAssertionVerifier() {
        log.warn("Step-up biometric matcher not configured — BIOMETRIC step-up will fail closed.");
        return (tenantId, actorId, assertion) -> {
            throw new StepUpUnavailableException("Biometric assertion verification is not configured");
        };
    }
}
