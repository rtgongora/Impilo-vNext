package zw.gov.mohcc.impilo.vito.core.biometric.engine;

import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricTemplateEntity;

import java.util.List;

/**
 * Pluggable biometric comparison. Production deployments must register a vendor-backed engine.
 *
 * <p>The default {@link FailClosedMatchingEngine} fails closed: it never fabricates a match
 * ({@code verify(...)} returns {@code UNAVAILABLE}/0.0, {@code identify(...)} returns no
 * candidates) so person-identity verification denies and stays auditable until a genuine engine
 * is registered as a {@code @Primary} bean of this type.</p>
 */
public interface BiometricMatchingEngine {

    /**
     * 1:1 verification against a single enrolled template row.
     */
    default VerificationDecision verify(byte[] probeTemplate, BiometricTemplateEntity enrolled) {
        return verify(probeTemplate, enrolled, BiometricProbeContext.EMPTY);
    }

    /**
     * 1:1 verification with optional liveness / attestation hints for vendor engines.
     */
    VerificationDecision verify(
            byte[] probeTemplate, BiometricTemplateEntity enrolled, BiometricProbeContext probeContext);

    /**
     * 1:N identification over the supplied candidate templates (already tenant- and modality-scoped).
     */
    default List<IdentificationCandidate> identify(
            byte[] probeTemplate, List<BiometricTemplateEntity> candidates) {
        return identify(probeTemplate, candidates, BiometricProbeContext.EMPTY);
    }

    List<IdentificationCandidate> identify(
            byte[] probeTemplate,
            List<BiometricTemplateEntity> candidates,
            BiometricProbeContext probeContext);

    record VerificationDecision(String result, double confidence, String summary) {}

    record IdentificationCandidate(java.util.UUID healthId, double confidence, String summary) {}
}
