package zw.gov.mohcc.impilo.mushex.api.dto;

/**
 * Optional body for a settlement release. All fields are optional: when the release total
 * meets/exceeds the high-value step-up threshold and a biometric probe is supplied, the
 * release is verified through the shared biometric seam. Absent fields ⇒ unchanged release
 * behaviour (the existing non-biometric step-up factor applies).
 */
public record ReleasePayoutsRequest(
        String biometricSubjectRef,
        String biometricModality,
        String biometricProbeBase64
) {}
