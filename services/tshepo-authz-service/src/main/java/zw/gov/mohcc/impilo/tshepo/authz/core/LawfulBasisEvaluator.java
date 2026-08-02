package zw.gov.mohcc.impilo.tshepo.authz.core;

import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.LawfulBasisDecision;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.LawfulBasisType;

import java.util.Locale;

/**
 * Decides <em>which lawful basis</em> permits an access, before asking whether consent exists.
 *
 * <p>Doctrine (trust-plane §2 and §7): <em>"Consent (a person's permission) ≠ Lawful basis (the
 * legal ground, which may exist without consent)"</em>, and a lawful non-consent basis must not be
 * denied merely because consent is absent.</p>
 *
 * <h2>What this corrects</h2>
 *
 * <p>{@code PolicyEngine.requiresConsent} recognised exactly two shapes: purposes that skip consent
 * ({@code EMERGENCY}, {@code BREAK_GLASS}, {@code SYSTEM}) and everything else, which needs it. Two
 * consequences, both wrong:</p>
 *
 * <ul>
 *   <li><b>{@code PUBLIC_HEALTH} and {@code REGULATORY_DUTY} were treated as consent-requiring.</b>
 *       A public-health or statutory access to a clinical resource was denied whenever no consent
 *       directive existed — denying a lawful basis for the absence of a different one.</li>
 *   <li><b>The skips recorded nothing.</b> Returning "no consent needed" is not the same as
 *       recording <em>why</em> the access was lawful. An audit trail that cannot say which ground
 *       was relied on cannot answer the only question that matters afterwards.</li>
 * </ul>
 *
 * <p>This class names the basis explicitly so it can be recorded, and so
 * {@link LawfulBasisType#EXPLICIT_CONSENT} remains the one basis that still has to be proven by
 * asking the consent service. The v1 contract enforces that separation itself: constructing a
 * {@code LawfulBasisDecision} with {@code EXPLICIT_CONSENT} throws.</p>
 *
 * <h2>Deliberately NOT claimed: direct care</h2>
 *
 * <p>{@link LawfulBasisType#DIRECT_CARE_RELATIONSHIP} is a real basis and is <strong>never
 * returned here</strong>, because this service cannot prove one. The care relationship lives in
 * PCT ({@code ClinicalAccessGuard#requireCareRelationship}); {@code AuthzInternalRequest} carries
 * no field asserting it. Returning it on the strength of {@code purpose=TREATMENT} would mean any
 * caller could self-declare a treating relationship by setting a header — which is the confused
 * deputy this programme exists to close. {@code TREATMENT} therefore still requires explicit
 * consent here, and closing the gap means carrying a <em>verified</em> care relationship into the
 * decision, not inferring one.</p>
 */
public final class LawfulBasisEvaluator {

    /**
     * The outcome of asking "on what ground is this access lawful?".
     *
     * @param requiresExplicitConsent when true, no non-consent basis applies and the consent
     *                                service must be asked
     * @param decision                the established non-consent basis, or null
     */
    public record Outcome(boolean requiresExplicitConsent, LawfulBasisDecision decision) {

        public static Outcome consent() {
            return new Outcome(true, null);
        }

        public static Outcome established(LawfulBasisType type, String reference, String reasonCode,
                                          String subjectRef, String purpose) {
            // The contract's permit() carries no reasonCode -- it is a field for denials. The
            // reason is kept on the audit line instead, where it belongs, rather than smuggled
            // into a permit by constructing the record directly.
            return new Outcome(false,
                    LawfulBasisDecision.permit(type, reference, subjectRef, purpose));
        }

        /** A label safe for a metric tag — closed vocabulary, never request data. */
        public String metricLabel() {
            return requiresExplicitConsent ? "EXPLICIT_CONSENT"
                    : decision == null ? "NONE" : decision.basisType().name();
        }
    }

    private LawfulBasisEvaluator() {
    }

    /**
     * @param request the request under evaluation
     * @param purpose the validated purpose of use (never client text — the parsed enum)
     * @param breakGlassVerified whether a break-glass request was actually verified upstream, NOT
     *                           merely whether the caller asked for that purpose
     * @return the ground on which this access is lawful, or a requirement to prove consent
     */
    public static Outcome evaluate(AuthzInternalRequest request, PurposeOfUse purpose,
                                   boolean breakGlassVerified) {
        if (purpose == null) {
            return Outcome.consent();
        }
        String subject = request == null ? null : request.subjectId();
        String purposeName = purpose.name();

        return switch (purpose) {
            // Emergency access is lawful in its own right, but ONLY where the upstream
            // break-glass control actually verified it. A bare purpose header is an unverified
            // claim, and treating it as a basis would make the control theatre -- the same hole
            // PolicyEngine's break-glass step exists to close.
            case BREAK_GLASS -> breakGlassVerified
                    ? Outcome.established(LawfulBasisType.EMERGENCY_BREAK_GLASS,
                        request == null ? null : request.escalationGrantId(),
                        "BREAK_GLASS_VERIFIED", subject, purposeName)
                    : Outcome.consent();

            case EMERGENCY -> Outcome.established(LawfulBasisType.EMERGENCY_BREAK_GLASS, null,
                    "EMERGENCY_CARE", subject, purposeName);

            case PUBLIC_HEALTH -> Outcome.established(LawfulBasisType.PUBLIC_HEALTH_AUTHORITY, null,
                    "PUBLIC_HEALTH_MANDATE", subject, purposeName);

            case REGULATORY_DUTY -> Outcome.established(LawfulBasisType.STATUTORY_AUTHORITY, null,
                    "REGULATORY_DUTY", subject, purposeName);

            // A platform/system action carries no human subject to consent on behalf of. It is
            // governed by the workload's own authorization, not by a person's permission.
            case SYSTEM -> Outcome.established(LawfulBasisType.OTHER_GOVERNED, null,
                    "SYSTEM_OPERATION", subject, purposeName);

            // TREATMENT, PAYMENT, OPERATIONS, RESEARCH: consent is the ground this service can
            // actually verify. See the class note on DIRECT_CARE_RELATIONSHIP.
            default -> Outcome.consent();
        };
    }

    /** Rollout mode, mirroring the other staged controls in this engine. */
    public enum Mode {
        /** Do not evaluate. Pre-Checkpoint-5 behaviour. */
        OFF,
        /** Evaluate and record, but let the existing consent requirement decide. */
        SHADOW,
        /** Apply: a established non-consent basis permits without asking for consent. */
        ENFORCE;

        public static Mode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return SHADOW;
            }
            String v = raw.trim().toUpperCase(Locale.ROOT);
            for (Mode m : values()) {
                if (m.name().equals(v)) {
                    return m;
                }
            }
            // A typo must not silently become OFF; that would disable the control while the
            // configuration claims otherwise.
            throw new IllegalArgumentException(
                    "tshepo.authz.lawful-basis-mode is '" + raw + "'; expected OFF, SHADOW or ENFORCE");
        }
    }
}
