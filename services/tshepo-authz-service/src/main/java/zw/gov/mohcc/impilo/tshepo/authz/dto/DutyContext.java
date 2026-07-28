package zw.gov.mohcc.impilo.tshepo.authz.dto;

/**
 * The introspected WORK_CONTEXT duty token, resolved by the PDP from
 * tshepo-identity {@code /tokens/introspect}. When {@link #usable()} is true the
 * operational context here was minted by the BFF against a live, ACTIVE Vashandi
 * assignment and is server-side valid (not revoked, not expired) — so the PDP may
 * treat it as the AUTHORITATIVE operational context and validate the loose picker
 * trust headers against it, rather than trusting the client alone.
 *
 * @param present    a WORK_CONTEXT token header was carried on the request
 * @param active     introspection returned active=true (not revoked / not expired)
 * @param tokenKind  the token kind reported by identity (expected {@code WORK_CONTEXT})
 * @param actorId    the actor the token was minted for (must match the request actor)
 * @param facilityId proven facility
 * @param workspaceId proven workspace (nullable)
 * @param departmentId proven department (nullable, opaque)
 * @param wardId     proven ward / unit (nullable, opaque)
 * @param programmeId proven programme (nullable, opaque)
 * @param orgId      proven organisation (nullable)
 * @param providerId proven provider public id (nullable)
 * @param role       proven duty role (roleTemplateId) — foldable into policy roles
 * @param assignmentId the Vashandi assignment id the proof was bound to (nullable)
 * @param workMode the minted {@link zw.gov.mohcc.impilo.tshepo.contracts.enums.WorkMode}
 *                 name (Phase B). Read ONLY from the token, never a header — the same
 *                 reasoning as {@code jurisdictionCode}: a header-supplied mode would let
 *                 a caller widen their own authority.
 * @param clinicalDataAccess the mode-derived clinical-data envelope (IDENTIFIED |
 *                           AGGREGATE | NONE) — see {@link #allowsIdentifiedClinicalRead()}.
 * @param contextId the resolved work context id (Phase C {@code ResolvedWorkContext.contextId})
 *                  this token was proven against (nullable until Phase C is wired)
 * @param servicePointId proven service point (nullable, opaque)
 */
public record DutyContext(
        boolean present,
        boolean active,
        String tokenKind,
        String actorId,
        String facilityId,
        String workspaceId,
        String departmentId,
        String wardId,
        String programmeId,
        String orgId,
        String providerId,
        String role,
        String assignmentId,
        /**
         * Jurisdiction the duty covers (NATIONAL, or a province/district code). A regulator's
         * authority is bounded by WHERE as well as by which organisation, so this is a dimension
         * in its own right rather than something inferred from the organisation.
         */
        String jurisdictionCode,
        String workMode,
        String clinicalDataAccess,
        String contextId,
        String servicePointId
) {
    /** No token was carried — the common, non-duty request. */
    public static DutyContext absent() {
        return new DutyContext(false, false, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    /** A token was carried but introspection said inactive (revoked / expired / unknown jti). */
    public static DutyContext revoked() {
        return new DutyContext(true, false, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    /** Present, active, and a genuine WORK_CONTEXT token: safe to treat as duty-authoritative. */
    public boolean usable() {
        return present && active && "WORK_CONTEXT".equalsIgnoreCase(tokenKind);
    }

    /**
     * True only when the duty mode's clinical-data envelope is IDENTIFIED — the sole
     * level at which a clinical canonical role is folded into the effective role set
     * (see {@code PolicyEngine.bindWorkContext}). A management/support/regulatory
     * session (AGGREGATE or NONE) never satisfies this, regardless of the raw
     * roleTemplateId carried.
     */
    public boolean allowsIdentifiedClinicalRead() {
        return usable() && "IDENTIFIED".equalsIgnoreCase(clinicalDataAccess);
    }
}
