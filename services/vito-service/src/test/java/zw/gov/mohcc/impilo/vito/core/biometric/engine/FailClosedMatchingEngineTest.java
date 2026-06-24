package zw.gov.mohcc.impilo.vito.core.biometric.engine;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.vito.core.biometric.engine.BiometricMatchingEngine.IdentificationCandidate;
import zw.gov.mohcc.impilo.vito.core.biometric.engine.BiometricMatchingEngine.VerificationDecision;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricTemplateEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G017: the default VITO biometric engine must never fabricate a MATCH. It fails closed so
 * person-identity verification denies (and 1:N identification yields no candidates) until a real
 * vendor matching engine is registered as a {@code @Primary} {@link BiometricMatchingEngine} bean.
 */
class FailClosedMatchingEngineTest {

    private final BiometricMatchingEngine engine = new FailClosedMatchingEngine(0.0);

    @Test
    void verifyNeverReturnsMatchEvenWhenProbeDigestEqualsTemplateHash() {
        byte[] probe = "fingerprint-bytes".getBytes(StandardCharsets.UTF_8);
        BiometricTemplateEntity enrolled = new BiometricTemplateEntity();
        // Bait the old SHA-256 equality path: store the exact probe digest as the template hash.
        enrolled.setTemplateHash(sha256Hex(probe));

        VerificationDecision decision = engine.verify(probe, enrolled, BiometricProbeContext.EMPTY);

        assertThat(decision.result()).isNotEqualTo("MATCH");
        assertThat(decision.result()).isEqualTo("UNAVAILABLE");
        assertThat(decision.confidence()).isZero();
        assertThat(decision.summary()).contains("fails closed");
    }

    @Test
    void verifyReturnsNoReferenceForMissingInputs() {
        assertThat(engine.verify(null, new BiometricTemplateEntity(), BiometricProbeContext.EMPTY).result())
                .isEqualTo("NO_REFERENCE");
        assertThat(engine.verify("p".getBytes(StandardCharsets.UTF_8), null, BiometricProbeContext.EMPTY).result())
                .isEqualTo("NO_REFERENCE");
    }

    @Test
    void verifyEnforcesConfiguredLivenessThresholdBeforeFailingClosed() {
        BiometricMatchingEngine livenessGated = new FailClosedMatchingEngine(0.8);
        byte[] probe = "fingerprint-bytes".getBytes(StandardCharsets.UTF_8);
        BiometricTemplateEntity enrolled = new BiometricTemplateEntity();
        enrolled.setTemplateHash(sha256Hex(probe));

        VerificationDecision belowThreshold = livenessGated.verify(
                probe, enrolled, new BiometricProbeContext(0.4, null, null));
        assertThat(belowThreshold.result()).isEqualTo("LIVENESS_REQUIRED");
        assertThat(belowThreshold.confidence()).isZero();

        // Liveness satisfied still must NOT yield a fabricated match — it fails closed.
        VerificationDecision livenessOk = livenessGated.verify(
                probe, enrolled, new BiometricProbeContext(0.9, null, null));
        assertThat(livenessOk.result()).isNotEqualTo("MATCH");
        assertThat(livenessOk.result()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void identifyNeverReturnsCandidatesEvenOnDigestEquality() {
        byte[] probe = "fingerprint-bytes".getBytes(StandardCharsets.UTF_8);
        BiometricTemplateEntity match = new BiometricTemplateEntity();
        match.setHealthId(UUID.randomUUID());
        match.setTemplateHash(sha256Hex(probe));

        List<IdentificationCandidate> hits =
                engine.identify(probe, List.of(match), BiometricProbeContext.EMPTY);

        assertThat(hits).isEmpty();
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
