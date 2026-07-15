package zw.gov.mohcc.impilo.pct.integration;

import java.util.List;
import java.util.UUID;

/**
 * Resolves the on-call trauma team for a facility from the VASHANDI workforce system of record.
 *
 * <p>This is a deliberately narrow, swappable seam. The current implementation
 * ({@link VashandiPoolOnCallResolver}) reads VASHANDI's existing on-duty virtual-pool endpoint,
 * modelling the trauma panel as a duty pool ({@code trauma-oncall:<facilityId>}) — the on-duty pool
 * membership IS the designated trauma team. It is a READ-ONLY projection over the workforce SoR, not
 * a parallel roster. A future post-gate upgrade to a dedicated one-call VASHANDI endpoint
 * (e.g. {@code GET /vashandi/on-call?facilityId=&cadres=}) is a drop-in behind this interface with
 * no trauma-activation rework.</p>
 */
public interface VashandiOnCallResolver {

    /** One resolved trauma-team member (a VASHANDI workforce profile on duty for the trauma panel). */
    record ResolvedTeamMember(String workforceProfileId, String role, String shiftId) {}

    /**
     * The on-call trauma team for {@code facilityId} right now. Returns an empty list when VASHANDI is
     * unreachable or the panel has no on-duty members — a trauma activation is never blocked by a
     * degraded workforce service (the empty resolution is honestly visible, not faked).
     */
    List<ResolvedTeamMember> resolveTraumaTeam(UUID tenantId, UUID facilityId);
}
