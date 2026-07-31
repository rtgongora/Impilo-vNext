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
    public static String deriveResourceType(String path) {
        if (path == null || path.length() < 2) return "UNKNOWN";
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String seg = segments[i];
            if (!seg.isBlank() && !seg.matches("[0-9a-fA-F-]{36}") && !seg.equals("v1") && !seg.equals("api")) {
                return seg;
            }
        }
        return "UNKNOWN";
    }

    /**
     * Derive resource ID (UUID) from the URL path.
     */
    public static String deriveResourceId(String path) {
        if (path == null) return null;
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            if (segments[i].matches("[0-9a-fA-F-]{36}")) {
                return segments[i];
            }
        }
        return null;
    }
}
