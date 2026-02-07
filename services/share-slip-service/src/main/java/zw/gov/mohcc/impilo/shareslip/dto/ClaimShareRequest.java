package zw.gov.mohcc.impilo.shareslip.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to claim a shared document link.
 *
 * @param shareToken        the share token from the QR code or URL
 * @param otp               the one-time password (required for OTP verification method)
 * @param deviceFingerprint  optional device fingerprint for audit trail
 */
public record ClaimShareRequest(
        @NotBlank(message = "shareToken is required")
        String shareToken,

        String otp,

        String deviceFingerprint
) {
}
