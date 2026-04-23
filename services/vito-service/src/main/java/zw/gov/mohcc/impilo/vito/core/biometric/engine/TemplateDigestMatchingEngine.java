package zw.gov.mohcc.impilo.vito.core.biometric.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricTemplateEntity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Deterministic matcher: treats a verification match when {@code SHA-256(probe)} equals the stored
 * {@link BiometricTemplateEntity#getTemplateHash()}. Identification ranks all templates whose hash
 * equals the probe digest (typically zero or one hit).
 */
@Component
public class TemplateDigestMatchingEngine implements BiometricMatchingEngine {

    private final double livenessMinScore;

    public TemplateDigestMatchingEngine(
            @Value("${vito.biometric.liveness-min-score:0}") double livenessMinScore) {
        this.livenessMinScore = livenessMinScore;
    }

    @Override
    public VerificationDecision verify(
            byte[] probeTemplate, BiometricTemplateEntity enrolled, BiometricProbeContext probeContext) {
        if (probeTemplate == null || enrolled == null) {
            return new VerificationDecision("NO_REFERENCE", 0.0, "Missing probe or enrolled template");
        }
        if (livenessMinScore > 0) {
            if (probeContext == null
                    || probeContext.livenessScore() == null
                    || probeContext.livenessScore() < livenessMinScore) {
                return new VerificationDecision(
                        "LIVENESS_REQUIRED",
                        0.0,
                        "Configured liveness threshold not satisfied (vito.biometric.liveness-min-score="
                                + livenessMinScore
                                + ")");
            }
        }
        String probeHash = sha256Hex(probeTemplate);
        if (probeHash.equals(enrolled.getTemplateHash())) {
            return new VerificationDecision("MATCH", 1.0, "Template digest equality (vendor engine not configured)");
        }
        return new VerificationDecision("NO_MATCH", 0.0, "Probe digest did not match enrolled template hash");
    }

    @Override
    public List<IdentificationCandidate> identify(
            byte[] probeTemplate, List<BiometricTemplateEntity> candidates, BiometricProbeContext probeContext) {
        if (probeTemplate == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        String probeHash = sha256Hex(probeTemplate);
        List<IdentificationCandidate> hits = new ArrayList<>();
        for (BiometricTemplateEntity t : candidates) {
            if (probeHash.equals(t.getTemplateHash())) {
                hits.add(new IdentificationCandidate(t.getHealthId(), 1.0,
                        "Digest match for template id=" + t.getId()));
            }
        }
        hits.sort(Comparator.comparingDouble(IdentificationCandidate::confidence).reversed());
        return hits;
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
