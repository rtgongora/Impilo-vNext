package zw.gov.mohcc.impilo.pharmacy.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for claiming a medication pickup using a token.
 *
 * <p>Optionally carries a live biometric probe so the collector's identity can be
 * verified through the shared biometric seam at collection: MATCH → collected-with-biometric;
 * NO_MATCH → collection rejected; UNAVAILABLE → fall back to the token check (unchanged).</p>
 *
 * @param token                the OTP or claim token
 * @param deviceFingerprint    the device fingerprint for audit purposes
 * @param collectorIdentity    OF-B16: identity of the person physically collecting
 *                             (recorded on the proof; defaults to delegate/actor)
 * @param biometricSubjectRef  reference of the enrolled subject to match against (optional)
 * @param biometricModality    "FINGERPRINT" | "FACE" | "IRIS" (defaults to FINGERPRINT)
 * @param biometricProbeBase64 the captured probe template, base64-encoded (optional)
 */
public record PickupClaimRequest(
        @NotBlank String token,
        String deviceFingerprint,
        String collectorIdentity,
        String biometricSubjectRef,
        String biometricModality,
        String biometricProbeBase64
) {}
