package zw.gov.mohcc.impilo.abis.engine;

/**
 * Vendor-agnostic probe metadata (liveness, attestation, engine routing). Raw biometric samples stay out of this object.
 */
public record BiometricProbeContext(
        Double livenessScore,
        String deviceAttestationRef,
        String engineHint) {

    public static final BiometricProbeContext EMPTY = new BiometricProbeContext(null, null, null);
}
