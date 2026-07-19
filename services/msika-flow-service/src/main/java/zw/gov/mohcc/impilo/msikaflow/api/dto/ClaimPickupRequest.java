package zw.gov.mohcc.impilo.msikaflow.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Pickup-claim redemption request. The token/OTP proof is mandatory; the biometric
 * collector-verify fields are OPTIONAL. When all three biometric fields are supplied
 * the collector's live probe is matched against the subject through the shared
 * verification seam at redemption; when they are absent the existing token/OTP proof
 * is unchanged.
 */
public record ClaimPickupRequest(
        @NotBlank String tokenOrOtp,
        String biometricSubjectRef,
        String biometricModality,
        String biometricProbeBase64
) {
    /** Backwards-compatible constructor for token/OTP-only claims (no biometric). */
    public ClaimPickupRequest(String tokenOrOtp) {
        this(tokenOrOtp, null, null, null);
    }
}
