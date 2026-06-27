package zw.gov.mohcc.impilo.tshepo.authz.stepup;

import java.util.Optional;
import java.util.UUID;

/**
 * Integration seams for the step-up modes whose verification logic is fully built
 * here but whose external dependency must be provided by the deployment. Default
 * implementations (see {@link StepUpProvidersConfig}) are fail-closed so an
 * unconfigured environment cannot complete those modes.
 */
public final class StepUpProviders {

    private StepUpProviders() {}

    /** Supplies an actor's enrolled TOTP shared secret (raw bytes). */
    public interface TotpSecretProvider {
        Optional<byte[]> secretFor(UUID tenantId, String actorId);
    }

    /** Delivers a one-time passcode to the actor out-of-band (e.g. SMS). */
    public interface OtpDeliveryAdapter {
        /** @throws StepUpUnavailableException if delivery is not configured (fail closed). */
        void deliver(UUID tenantId, String actorId, String otp);
    }

    /** Verifies a biometric assertion against the actor's enrolled template. */
    public interface BiometricAssertionVerifier {
        /** @throws StepUpUnavailableException if no matcher is configured (fail closed). */
        boolean verify(UUID tenantId, String actorId, String assertion);
    }
}
