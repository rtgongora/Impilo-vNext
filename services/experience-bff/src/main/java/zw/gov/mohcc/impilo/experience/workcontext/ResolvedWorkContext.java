package zw.gov.mohcc.impilo.experience.workcontext;

import java.util.List;
import java.util.Map;

/**
 * A single work context, unioned from one of six authoritative sources
 * (Vashandi, WGV, org-registry, TUSO facility-admin, support-service) into
 * the discriminated shape the Work Home picker/switcher and the duty-token
 * mint both consume. See {@code WorkContextResolutionService} for how the
 * union, dedup and ranking are computed.
 *
 * <p>{@code contextId} is deterministic — {@code sha256(sourceSystem|sourceRecordId)}
 * truncated — so the same physical duty resolves to the same id across calls,
 * which is what lets {@code POST /work-context/session} re-prove a
 * client-submitted {@code contextId} against source at mint time rather than
 * trusting it as a bearer credential.</p>
 *
 * @param contextId        stable id, see above
 * @param contextKind      facility | organisation | jurisdiction | programme | regulator | virtual | support
 * @param sourceSystem     VASHANDI | WGV | ORG_REGISTRY | TUSO | SUPPORT
 * @param sourceRecordId   the source system's own id for this record (may be a composite
 *                         {@code "SYSTEM_A:id+SYSTEM_B:id"} when two sources describe the
 *                         same physical duty and were merged, not dropped)
 * @param facilityId       anchor — nullable, present only for facility-anchored contexts
 * @param organisationId   anchor — nullable
 * @param jurisdictionCode anchor — nullable
 * @param programmeId      anchor — nullable
 * @param roleTemplateId   the raw duty-template/appointment-role id (heterogeneous key space —
 *                         Vashandi duty template | org-registry appointment role | TUSO
 *                         facility-admin role | WGV role_code)
 * @param canonicalRoles   canonical policy roles the role template maps to (via the authz catalog)
 * @param availableModes   {@link zw.gov.mohcc.impilo.tshepo.contracts.enums.WorkMode} names this
 *                         context may be entered in
 * @param defaultMode      the mode to pre-select, or null if none is marked default
 * @param modeSource       CATALOG | DERIVED_REMOTE | DERIVED_LOCAL — see {@code WorkModeResolution}
 * @param departmentId     nullable, opaque
 * @param departmentLabel  human label if resolved, else falls back to departmentId
 * @param wardId           nullable, opaque
 * @param servicePointId   nullable, opaque
 * @param workspaceId      nullable
 * @param shift            {shiftId, startsAt, endsAt, checkInState, supervisorConfirmed} or null
 * @param restrictions     e.g. LEAVE_ACTIVE, NO_ACTIVE_SHIFT, DEPARTMENT_UNVALIDATED,
 *                         APPROVAL_PENDING, SOURCE_DEGRADED
 * @param label            human-readable picker label, e.g. "Parirenyatwa — Oncology Ward B (Charge Nurse)"
 * @param groupHint        today | regular | virtual | oversight | other | personal
 * @param rankScore        the {@code WorkContextRanking} score, exposed for auditability
 */
public record ResolvedWorkContext(
        String contextId,
        String contextKind,
        String sourceSystem,
        String sourceRecordId,
        String facilityId,
        String organisationId,
        String jurisdictionCode,
        String programmeId,
        String roleTemplateId,
        List<String> canonicalRoles,
        List<String> availableModes,
        String defaultMode,
        String modeSource,
        String departmentId,
        String departmentLabel,
        String wardId,
        String servicePointId,
        String workspaceId,
        Map<String, Object> shift,
        List<String> restrictions,
        String label,
        String groupHint,
        int rankScore
) {
    /** Dedup/merge key — two source rows describing the same physical duty collapse to one. */
    public String dedupKey() {
        return String.join("|",
                String.valueOf(contextKind), String.valueOf(facilityId), String.valueOf(organisationId),
                String.valueOf(jurisdictionCode), String.valueOf(programmeId),
                String.valueOf(roleTemplateId), String.valueOf(departmentId));
    }

    public ResolvedWorkContext withRankScore(int newScore) {
        return new ResolvedWorkContext(contextId, contextKind, sourceSystem, sourceRecordId, facilityId,
                organisationId, jurisdictionCode, programmeId, roleTemplateId, canonicalRoles, availableModes,
                defaultMode, modeSource, departmentId, departmentLabel, wardId, servicePointId, workspaceId,
                shift, restrictions, label, groupHint, newScore);
    }
}
