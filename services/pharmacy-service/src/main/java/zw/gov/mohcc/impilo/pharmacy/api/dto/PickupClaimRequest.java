package zw.gov.mohcc.impilo.pharmacy.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for claiming a medication pickup using a token.
 *
 * @param token             the OTP or claim token
 * @param deviceFingerprint the device fingerprint for audit purposes
 */
public record PickupClaimRequest(
        @NotBlank String token,
        String deviceFingerprint
) {}
