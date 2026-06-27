package zw.gov.mohcc.impilo.tshepo.authz.stepup;

/**
 * Step-up verification modes. Each maps to a {@link StepUpVerifier}. TOTP and
 * SUPERVISOR_APPROVAL verify with self-contained server-side logic; SMS_OTP and
 * BIOMETRIC have fully-built verification logic but delegate their external
 * dependency (OTP delivery, biometric matching) to a fail-closed adapter that
 * must be configured before that mode can be used in production.
 */
public enum StepUpMode {
    TOTP,
    SMS_OTP,
    BIOMETRIC,
    SUPERVISOR_APPROVAL;

    /**
     * Resolve a mode from an explicit mode string or the legacy challenge type.
     * Legacy {@code MFA} maps to {@link #TOTP} (authenticator app) by default.
     */
    public static StepUpMode resolve(String modeOrChallengeType) {
        if (modeOrChallengeType == null || modeOrChallengeType.isBlank()) {
            return TOTP;
        }
        switch (modeOrChallengeType.trim().toUpperCase()) {
            case "TOTP":
            case "MFA":
                return TOTP;
            case "SMS_OTP":
            case "SMS":
                return SMS_OTP;
            case "BIOMETRIC":
                return BIOMETRIC;
            case "SUPERVISOR_APPROVAL":
            case "SUPERVISOR":
                return SUPERVISOR_APPROVAL;
            default:
                return TOTP;
        }
    }
}
