package zw.gov.mohcc.impilo.tshepo.authz.stepup;

import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.StepUpChallengeEntity;

/**
 * Verifies a step-up challenge for a specific {@link StepUpMode}.
 */
public interface StepUpVerifier {

    StepUpMode mode();

    /**
     * Prepare a challenge at issue time (default no-op). SMS_OTP generates, stores
     * (hashed) and delivers the OTP here; if delivery is not configured it throws
     * {@link StepUpUnavailableException} so an undeliverable challenge is never issued.
     */
    default void onIssue(StepUpChallengeEntity challenge) {
        // no-op by default
    }

    /**
     * @return true iff the submitted credential verifies the challenge.
     * @throws StepUpUnavailableException if the mode's provider is not configured (fail closed).
     */
    boolean verify(StepUpChallengeEntity challenge, String submittedCredential);

    // ── shared crypto helpers ────────────────────────────────────────────────

    /** Constant-time comparison of two ASCII strings. */
    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] x = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] y = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(x, y);
    }

    static String sha256Hex(String value) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
