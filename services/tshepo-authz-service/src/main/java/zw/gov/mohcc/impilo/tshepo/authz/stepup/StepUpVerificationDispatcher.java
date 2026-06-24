package zw.gov.mohcc.impilo.tshepo.authz.stepup;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.StepUpChallengeEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Selects and runs the {@link StepUpVerifier} for a challenge's {@link StepUpMode}.
 * Holds the four mode verifiers (built from the injected fail-closed seams), so the
 * service has a single fail-closed entry point.
 */
@Component
public class StepUpVerificationDispatcher {

    private final Map<StepUpMode, StepUpVerifier> verifiers = new EnumMap<>(StepUpMode.class);

    public StepUpVerificationDispatcher(StepUpProviders.TotpSecretProvider totpSecretProvider,
                                        StepUpProviders.OtpDeliveryAdapter otpDeliveryAdapter,
                                        StepUpProviders.BiometricAssertionVerifier biometricVerifier) {
        register(new TotpVerifier(totpSecretProvider));
        register(new SmsOtpVerifier(otpDeliveryAdapter));
        register(new BiometricVerifier(biometricVerifier));
        register(new SupervisorApprovalVerifier());
    }

    private void register(StepUpVerifier v) {
        verifiers.put(v.mode(), v);
    }

    /** @throws StepUpUnavailableException if no verifier exists for the mode (fail closed). */
    public StepUpVerifier require(StepUpMode mode) {
        StepUpVerifier v = verifiers.get(mode);
        if (v == null) {
            throw new StepUpUnavailableException("No step-up verifier for mode " + mode);
        }
        return v;
    }

    // ── TOTP (RFC 6238, HMAC-SHA1, 30s step, ±1 window) ──────────────────────
    static final class TotpVerifier implements StepUpVerifier {
        private static final int STEP_SECONDS = 30;
        private final StepUpProviders.TotpSecretProvider secretProvider;

        TotpVerifier(StepUpProviders.TotpSecretProvider secretProvider) {
            this.secretProvider = secretProvider;
        }

        public StepUpMode mode() { return StepUpMode.TOTP; }

        public boolean verify(StepUpChallengeEntity challenge, String code) {
            byte[] secret = secretProvider.secretFor(challenge.getTenantId(), challenge.getActorId())
                    .orElseThrow(() -> new StepUpUnavailableException(
                            "No enrolled TOTP secret for actor " + challenge.getActorId()));
            if (code == null || !code.matches("\\d{6}")) {
                return false;
            }
            long step = Instant.now().getEpochSecond() / STEP_SECONDS;
            for (long w = -1; w <= 1; w++) {
                if (StepUpVerifier.constantTimeEquals(generate(secret, step + w), code)) {
                    return true;
                }
            }
            return false;
        }

        private static String generate(byte[] secret, long counter) {
            try {
                byte[] msg = ByteBuffer.allocate(8).putLong(counter).array();
                Mac mac = Mac.getInstance("HmacSHA1");
                mac.init(new SecretKeySpec(secret, "HmacSHA1"));
                byte[] h = mac.doFinal(msg);
                int offset = h[h.length - 1] & 0xF;
                int binary = ((h[offset] & 0x7f) << 24)
                        | ((h[offset + 1] & 0xff) << 16)
                        | ((h[offset + 2] & 0xff) << 8)
                        | (h[offset + 3] & 0xff);
                return String.format("%06d", binary % 1_000_000);
            } catch (Exception e) {
                throw new IllegalStateException("TOTP generation failed", e);
            }
        }
    }

    // ── SMS OTP (server-generated, hashed at issue, delivered out-of-band) ────
    static final class SmsOtpVerifier implements StepUpVerifier {
        private static final SecureRandom RANDOM = new SecureRandom();
        private final StepUpProviders.OtpDeliveryAdapter delivery;

        SmsOtpVerifier(StepUpProviders.OtpDeliveryAdapter delivery) {
            this.delivery = delivery;
        }

        public StepUpMode mode() { return StepUpMode.SMS_OTP; }

        @Override
        public void onIssue(StepUpChallengeEntity challenge) {
            String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
            challenge.setVerificationSecretHash(StepUpVerifier.sha256Hex(otp));
            // Fail closed: if delivery is unconfigured this throws and the challenge is not issued.
            delivery.deliver(challenge.getTenantId(), challenge.getActorId(), otp);
        }

        public boolean verify(StepUpChallengeEntity challenge, String code) {
            if (code == null || code.isBlank() || challenge.getVerificationSecretHash() == null) {
                return false;
            }
            return StepUpVerifier.constantTimeEquals(
                    challenge.getVerificationSecretHash(), StepUpVerifier.sha256Hex(code));
        }
    }

    // ── Biometric (assertion verified by a configured matcher) ───────────────
    static final class BiometricVerifier implements StepUpVerifier {
        private final StepUpProviders.BiometricAssertionVerifier matcher;

        BiometricVerifier(StepUpProviders.BiometricAssertionVerifier matcher) {
            this.matcher = matcher;
        }

        public StepUpMode mode() { return StepUpMode.BIOMETRIC; }

        public boolean verify(StepUpChallengeEntity challenge, String assertion) {
            if (assertion == null || assertion.isBlank()) {
                return false;
            }
            // Throws StepUpUnavailableException if no matcher is configured (fail closed).
            return matcher.verify(challenge.getTenantId(), challenge.getActorId(), assertion);
        }
    }

    // ── Supervisor approval (server-recorded, dual control) ──────────────────
    static final class SupervisorApprovalVerifier implements StepUpVerifier {
        public StepUpMode mode() { return StepUpMode.SUPERVISOR_APPROVAL; }

        public boolean verify(StepUpChallengeEntity challenge, String ignoredCredential) {
            // Trust comes from a server-side approval recorded by an authorised supervisor
            // (see StepUpService.recordSupervisorApproval) — never a client-supplied name.
            return challenge.getApprovedBy() != null && !challenge.getApprovedBy().isBlank();
        }
    }
}
