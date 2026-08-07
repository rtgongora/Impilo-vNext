package zw.gov.mohcc.impilo.madi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.sharedkernel.security.EmergencyAccessGuard;
import zw.gov.mohcc.impilo.sharedkernel.security.EscalationGrantValidator;
import zw.gov.mohcc.impilo.sharedkernel.security.UngovernedOverrideRecorder;

/**
 * Wires the break-glass guard with the two collaborators the emergency-access ruling requires: a
 * validator that can tell "no grant" from "could not ask", and a recorder that makes the second case
 * reviewable.
 *
 * <p>Both are mandatory by construction — see {@link EmergencyAccessGuard}. There is no
 * configuration here that can produce a guard which allows without recording.</p>
 */
@Configuration
public class EmergencyAccessConfig {

    @Bean
    public EmergencyAccessGuard emergencyAccessGuard(EscalationGrantValidator validator,
                                                     UngovernedOverrideRecorder recorder) {
        return new EmergencyAccessGuard(validator, recorder);
    }
}
