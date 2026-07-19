package zw.gov.mohcc.impilo.abis.engine;

import zw.gov.mohcc.impilo.abis.persistence.entity.TemplateEntity;

import java.util.List;

/**
 * Default {@link BiometricMatchingEngine}: fails closed. No real biometric matching engine
 * (vendor SDK / matching service) is configured, so this default REFUSES to fabricate a match.
 *
 * <p>Biometric captures never produce byte-identical templates, so any template-hash equality
 * check would report a {@code MATCH} at confidence 1.0 that could not correspond to a genuine
 * biometric comparison — a dangerous result in a person-identity verification path. This engine
 * instead returns a non-MATCH outcome ({@code UNAVAILABLE}) at zero confidence for 1:1
 * verification and yields no candidates for 1:N identification, so verification denies and
 * remains fully auditable until a genuine engine is registered as a {@code @Primary}
 * {@link BiometricMatchingEngine} bean.</p>
 *
 * <p>The configured liveness threshold ({@code abis.matching.liveness-min-score}) is still
 * evaluated first so the audit trail distinguishes a liveness failure from "no engine
 * configured".</p>
 *
 * <p>Registered via {@code MatchingEngineConfig} with {@code @ConditionalOnMissingBean} so a
 * vendor SDK engine bean displaces it without any code change here.</p>
 */
public class FailClosedMatchingEngine implements BiometricMatchingEngine {

    private final double livenessMinScore;

    public FailClosedMatchingEngine(double livenessMinScore) {
        this.livenessMinScore = livenessMinScore;
    }

    @Override
    public VerificationDecision verify(
            byte[] probeTemplate, TemplateEntity enrolled, BiometricProbeContext probeContext) {
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
                        "Configured liveness threshold not satisfied (abis.matching.liveness-min-score="
                                + livenessMinScore
                                + ")");
            }
        }
        return new VerificationDecision(
                "UNAVAILABLE",
                0.0,
                "No biometric matching engine configured — verification fails closed (no fabricated "
                        + "match). Register a @Primary BiometricMatchingEngine to enable real matching.");
    }

    @Override
    public List<IdentificationCandidate> identify(
            byte[] probeTemplate, List<TemplateEntity> candidates, BiometricProbeContext probeContext) {
        // Fail closed: never fabricate identification candidates from byte-hash equality. A real
        // 1:N matcher must replace this engine to produce candidates.
        return List.of();
    }
}
