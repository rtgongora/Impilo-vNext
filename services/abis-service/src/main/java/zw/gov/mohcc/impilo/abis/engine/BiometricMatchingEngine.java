package zw.gov.mohcc.impilo.abis.engine;

import zw.gov.mohcc.impilo.abis.persistence.entity.TemplateEntity;

import java.util.List;

/**
 * Pluggable biometric comparison. Production deployments must register a vendor-backed engine.
 *
 * <p>The default {@link FailClosedMatchingEngine} fails closed: it never fabricates a match
 * ({@code verify(...)} returns {@code UNAVAILABLE}/0.0, {@code identify(...)} returns no
 * candidates) so person-identity verification denies and stays auditable until a genuine engine
 * is registered as a {@code @Primary} bean of this type.</p>
 *
 * <p>Moved from VITO ({@code zw.gov.mohcc.impilo.vito.core.biometric.engine}) per D6: ABIS is
 * the biometric template SoR; candidates are keyed by opaque {@code subject_ref}, never a
 * Health ID (TSHEPO holds the biometric-subject → HID mapping).</p>
 */
public interface BiometricMatchingEngine {

    /**
     * 1:1 verification against a single enrolled template row.
     */
    default VerificationDecision verify(byte[] probeTemplate, TemplateEntity enrolled) {
        return verify(probeTemplate, enrolled, BiometricProbeContext.EMPTY);
    }

    /**
     * 1:1 verification with optional liveness / attestation hints for vendor engines.
     */
    VerificationDecision verify(
            byte[] probeTemplate, TemplateEntity enrolled, BiometricProbeContext probeContext);

    /**
     * 1:N identification over the supplied candidate templates (already tenant- and modality-scoped).
     */
    default List<IdentificationCandidate> identify(
            byte[] probeTemplate, List<TemplateEntity> candidates) {
        return identify(probeTemplate, candidates, BiometricProbeContext.EMPTY);
    }

    List<IdentificationCandidate> identify(
            byte[] probeTemplate,
            List<TemplateEntity> candidates,
            BiometricProbeContext probeContext);

    /**
     * Extract a comparable template from a raw capture IMAGE (image → template).
     * Fail-closed by default; the engine-backed adapter implements it via the
     * matcher-engine. Enrolment-from-image = extract then {@code AbisTemplateService.enroll}.
     */
    default ExtractionResult extractTemplate(
            zw.gov.mohcc.impilo.abis.core.BiometricModality modality,
            byte[] sampleImage, int width, int height, int dpi) {
        return ExtractionResult.unavailable("extraction_unavailable");
    }

    record ExtractionResult(boolean ok, byte[] template, double quality, String detail) {
        public static ExtractionResult unavailable(String detail) {
            return new ExtractionResult(false, new byte[0], 0.0, detail);
        }
    }

    record VerificationDecision(String result, double confidence, String summary) {}

    /**
     * A 1:N candidate for human adjudication. {@code subjectRef} is the opaque ABIS subject
     * reference — never a Health ID and never an automatic merge decision.
     */
    record IdentificationCandidate(String subjectRef, double confidence, String summary) {}
}
