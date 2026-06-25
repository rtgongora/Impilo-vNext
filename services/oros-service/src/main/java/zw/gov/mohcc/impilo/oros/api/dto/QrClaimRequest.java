package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to claim a patient-carried order QR at a fulfilling facility. The OTP authenticates the
 * claim at share-slip; the OROS caller is additionally authenticated at the trust edge.
 */
public record QrClaimRequest(
        @NotBlank String shareToken,
        String otp,
        String deviceFingerprint
) {}
