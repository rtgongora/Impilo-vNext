package zw.gov.mohcc.impilo.tshepo.authz.dto;

import java.util.List;

/**
 * Captures full evidence of a single PolicyEngine authorization decision.
 *
 * <p>Published to the {@code trust.decision_evidence} topic on every evaluation
 * so downstream services can audit, replay, and reason over trust decisions.</p>
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code actorId} — the authenticated actor (user/service) making the request</li>
 *   <li>{@code actorType} — type of actor (PROVIDER, PATIENT, SYSTEM, etc.)</li>
 *   <li>{@code patientReference} — resource ID when the resource is a clinical type (nullable)</li>
 *   <li>{@code action} — the action attempted (e.g. {@code GET:/v1/patients})</li>
 *   <li>{@code consistencyClass} — "A" (destructive), "B" (mutation), or "C" (read)</li>
 *   <li>{@code decision} — ALLOW, DENY, or STEP_UP_REQUIRED</li>
 *   <li>{@code reasonCodes} — ordered list of reason codes explaining the decision</li>
 *   <li>{@code policyVersion} — version of the policy set that produced this decision</li>
 *   <li>{@code projectionStalenessMs} — age of the cached policy projection in ms (nullable)</li>
 * </ul>
 * </p>
 */
public record DecisionEvidenceDto(
        String actorId,
        String actorType,
        String patientReference,
        String action,
        String consistencyClass,
        String decision,
        List<String> reasonCodes,
        String policyVersion,
        Long projectionStalenessMs
) {}
