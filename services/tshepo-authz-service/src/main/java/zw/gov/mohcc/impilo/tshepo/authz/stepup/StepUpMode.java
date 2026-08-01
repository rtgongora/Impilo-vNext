package zw.gov.mohcc.impilo.tshepo.authz.stepup;

/**
 * Legacy governed-workflow step-up modes. Authentication factors are owned by
 * Keycloak and therefore cannot be issued or verified by Tshepo. The TOTP and
 * SMS values remain only so historical challenge rows can be interpreted and
 * rejected explicitly instead of being silently reclassified.
 */
public enum StepUpMode {
    TOTP,
    SMS_OTP,
    BIOMETRIC,
    SUPERVISOR_APPROVAL;

    /**
     * Resolve a governed non-authenticator workflow. Keycloak-native factors and
     * absent/unknown values fail closed; they never fall back to TOTP.
     */
    public static StepUpMode resolve(String modeOrChallengeType) {
        if (modeOrChallengeType == null || modeOrChallengeType.isBlank()) {
            throw new IllegalArgumentException("An explicit governed step-up mode is required");
        }
        switch (modeOrChallengeType.trim().toUpperCase()) {
            case "TOTP":
            case "MFA":
            case "SMS_OTP":
            case "SMS":
                throw new StepUpUnavailableException(
                        "Authentication-factor challenges are Keycloak-native; start an OIDC step-up transaction");
            case "BIOMETRIC":
                return BIOMETRIC;
            case "SUPERVISOR_APPROVAL":
            case "SUPERVISOR":
                return SUPERVISOR_APPROVAL;
            default:
                throw new IllegalArgumentException("Unsupported step-up mode: " + modeOrChallengeType);
        }
    }
}
