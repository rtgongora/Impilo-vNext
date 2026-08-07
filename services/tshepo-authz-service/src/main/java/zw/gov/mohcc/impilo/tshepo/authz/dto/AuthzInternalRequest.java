package zw.gov.mohcc.impilo.tshepo.authz.dto;

import java.util.List;
import java.util.UUID;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance;

/**
 * Internal authorization request used by the PolicyEngine.
 * Aggregates all context needed to make an authorization decision.
 */
public record AuthzInternalRequest(
        UUID tenantId,
        String actorId,
        String actorType,
        List<String> roles,
        String purposeOfUse,
        String deviceFingerprint,
        UUID correlationId,
        UUID facilityId,
        UUID workspaceId,
        String shiftId,
        String method,
        String path,
        String action,
        String resourceType,
        String resourceId,
        int loaLevel,
        String sessionId,
        String bearerToken,
        AuthenticationAssurance authenticationAssurance,
        // ── 10-dimension access control fields (doctrine alignment) ──────
        String providerId,
        String departmentId,
        String wardId,
        String programmeId,
        String subjectId,
        String assuranceLevel,
        /** Optional active workflow escalation grant (x-escalation-grant-id). */
        String escalationGrantId,
        /** Optional workflow / review context (x-workflow-state). */
        String workflowContext,
        /**
         * The introspected WORK_CONTEXT duty token (x-work-context-token), or
         * {@link DutyContext#absent()} when none was carried. When {@code usable()},
         * its operational context is the authoritative facility/dept/ward/org/role
         * for this decision; the loose headers above are validated against it.
         */
        DutyContext dutyContext
) {
    /** Compatibility constructor for callers that have not yet supplied token-derived AAL. */
    public AuthzInternalRequest(
            UUID tenantId, String actorId, String actorType, List<String> roles,
            String purposeOfUse, String deviceFingerprint, UUID correlationId,
            UUID facilityId, UUID workspaceId, String shiftId, String method, String path,
            String action, String resourceType, String resourceId, int loaLevel,
            String sessionId, String bearerToken, String providerId, String departmentId,
            String wardId, String programmeId, String subjectId, String assuranceLevel,
            String escalationGrantId, String workflowContext, DutyContext dutyContext) {
        this(tenantId, actorId, actorType, roles, purposeOfUse, deviceFingerprint,
                correlationId, facilityId, workspaceId, shiftId, method, path, action,
                resourceType, resourceId, loaLevel, sessionId, bearerToken,
                AuthenticationAssurance.none(), providerId, departmentId, wardId,
                programmeId, subjectId, assuranceLevel, escalationGrantId,
                workflowContext, dutyContext);
    }

    /**
     * Return a copy with the given roles (used to fold the WORK_CONTEXT duty role into
     * the effective role set — additive, never removing a Keycloak-claim role).
     */
    public AuthzInternalRequest withRoles(List<String> newRoles) {
        return new AuthzInternalRequest(
                tenantId, actorId, actorType, newRoles, purposeOfUse, deviceFingerprint,
                correlationId, facilityId, workspaceId, shiftId, method, path, action,
                resourceType, resourceId, loaLevel, sessionId, bearerToken, authenticationAssurance, providerId,
                departmentId, wardId, programmeId, subjectId, assuranceLevel,
                escalationGrantId, workflowContext, dutyContext);
    }

    /** Return a copy carrying assurance derived from a validated authentication token. */
    public AuthzInternalRequest withAuthenticationAssurance(AuthenticationAssurance assurance) {
        return new AuthzInternalRequest(
                tenantId, actorId, actorType, roles, purposeOfUse, deviceFingerprint,
                correlationId, facilityId, workspaceId, shiftId, method, path, action,
                resourceType, resourceId, loaLevel, sessionId, bearerToken,
                assurance == null ? AuthenticationAssurance.none() : assurance, providerId,
                departmentId, wardId, programmeId, subjectId, assuranceLevel,
                escalationGrantId, workflowContext, dutyContext);
    }

    /**
     * Derive a human-readable action from method + path.
     */
    public static String deriveAction(String method, String path) {
        if (method == null) return "UNKNOWN";
        return method.toUpperCase() + ":" + (path != null ? path : "/");
    }

    /**
     * Derive resource type from the URL path.
     */
    /**
     * A path segment that names an INSTANCE rather than a kind of thing.
     *
     * <p>This used to recognise only a 36-character UUID, and that gap silently disabled consent.
     * {@code /v1/patients/cpid-12345} derived a resource type of {@code "cpid-12345"} — which is
     * absent from {@code PolicyEngine.CLINICAL_RESOURCE_TYPES}, so {@code requiresConsent} returned
     * false and the read was never consent-checked. The same gap left
     * {@link #deriveResourceId} returning null, so even a consent evaluation that did fire would
     * have had no subject to evaluate against. Measured 2026-08-07: every {@code patient_ref} in
     * {@code tshepo_consent.consent_directive} is {@code cpid-…} form, i.e. non-UUID — so this was
     * not a corner case, it was the normal shape of a per-record clinical read.
     *
     * <p>The rule: <strong>a resource TYPE is a vocabulary word; an identifier carries a digit.</strong>
     * Verified against the live decision log — of 128 distinct resource types observed, the only
     * two containing a digit were a map tile and a patient CPID, both of which are identifiers.
     * Collection names in this estate ({@code patients}, {@code encounters}, {@code clinical-notes})
     * are letters and hyphens.
     *
     * <p>Deliberately conservative in the safe direction: misreading a type as an identifier makes
     * the derivation fall back to the parent segment, which is a broader, more governed name.
     * Misreading an identifier as a type is what caused the consent gap.
     */
    /**
     * Route scaffolding: neither a kind of thing nor an instance of one. Enumerated because
     * {@code v1} carries a digit and would otherwise be classified as an identifier — which it was,
     * in the first cut of this fix: {@code deriveResourceId("/v1/patients")} returned {@code "v1"},
     * putting a version string where the consent subject belongs.
     */
    private static final java.util.Set<String> STRUCTURAL_SEGMENTS =
            java.util.Set.of("v1", "v2", "api", "internal");

    static boolean isIdentifierSegment(String seg) {
        if (seg == null || seg.isBlank()) return false;
        if (STRUCTURAL_SEGMENTS.contains(seg)) return false;
        return seg.matches("[0-9a-fA-F-]{36}")   // canonical UUID
                || seg.matches(".*[0-9].*");     // any digit ⇒ an instance, not a kind
    }

    public static String deriveResourceType(String path) {
        if (path == null || path.length() < 2) return "UNKNOWN";
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String seg = segments[i];
            if (!seg.isBlank() && !isIdentifierSegment(seg) && !seg.equals("v1") && !seg.equals("api")) {
                return seg;
            }
        }
        return "UNKNOWN";
    }

    /**
     * Derive resource ID (UUID) from the URL path.
     */
    /**
     * The instance this request names, or null for a collection.
     *
     * <p>Recognises the same identifier shapes as {@link #isIdentifierSegment}, not UUIDs alone.
     * On the consent path this value becomes {@code subjectRef} — the person whose consent governs
     * the access — so a UUID-only rule meant a per-record read of {@code /v1/patients/cpid-12345}
     * carried no subject at all.
     */
    public static String deriveResourceId(String path) {
        if (path == null) return null;
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            if (isIdentifierSegment(segments[i])) {
                return segments[i];
            }
        }
        return null;
    }
}
