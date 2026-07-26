package zw.gov.mohcc.impilo.vashandi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ShiftEntity;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<ShiftEntity, UUID> {

    List<ShiftEntity> findByTenantIdAndRosterIdOrderByStartTimeAsc(UUID tenantId, UUID rosterId);

    Optional<ShiftEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ShiftEntity> findByTenantIdAndWorkforceProfileIdAndStartTimeBetweenOrderByStartTimeAsc(
            UUID tenantId, UUID workforceProfileId, OffsetDateTime start, OffsetDateTime end);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    // ---- theatre case-team shift coverage (V007) -------------------------

    /** Count a member's rostered/on-duty shifts (soft coverage signal). */
    long countByTenantIdAndWorkforceProfileIdAndStatusIn(
            UUID tenantId, UUID workforceProfileId, Collection<String> statuses);

    long countByTenantIdAndFacilityIdAndStatus(UUID tenantId, UUID facilityId, String status);

    // ---- virtual-pool duty (V006 partial index) --------------------------

    /**
     * Shifts on duty for a virtual pool RIGHT NOW: window covers {@code at}
     * (inclusive edges) and the status is an on-duty status
     * ('confirmed'/'checked_in' — scheduled is a plan, cancelled/swapped are
     * not coverage).
     */
    @Query("SELECT s FROM ShiftEntity s WHERE s.tenantId = :tenantId AND s.virtualPoolId = :poolId "
            + "AND s.status IN :statuses AND s.startTime <= :at AND s.endTime >= :at "
            + "ORDER BY s.startTime ASC")
    List<ShiftEntity> findOnDutyForPool(@Param("tenantId") UUID tenantId,
                                        @Param("poolId") String poolId,
                                        @Param("statuses") Collection<String> statuses,
                                        @Param("at") OffsetDateTime at);

    /** Next upcoming shift start for a pool (rostered coverage lookahead). */
    @Query("SELECT MIN(s.startTime) FROM ShiftEntity s WHERE s.tenantId = :tenantId "
            + "AND s.virtualPoolId = :poolId AND s.status IN :statuses AND s.startTime > :after")
    OffsetDateTime findNextShiftStartForPool(@Param("tenantId") UUID tenantId,
                                             @Param("poolId") String poolId,
                                             @Param("statuses") Collection<String> statuses,
                                             @Param("after") OffsetDateTime after);

    /** Distinct virtual-pool keys that have at least one rostered shift for this tenant. */
    @Query("SELECT DISTINCT s.virtualPoolId FROM ShiftEntity s WHERE s.tenantId = :tenantId "
            + "AND s.virtualPoolId IS NOT NULL ORDER BY s.virtualPoolId ASC")
    List<String> findDistinctVirtualPools(@Param("tenantId") UUID tenantId);

    /** Every shift rostered against a pool, any status (management read). */
    List<ShiftEntity> findByTenantIdAndVirtualPoolIdOrderByStartTimeAsc(UUID tenantId, String virtualPoolId);

    /**
     * Roster-week read (V009): every shift at a facility whose window starts inside [from, to).
     * Powers the roster grid and the control tower, both of which have been rendering empty
     * because the BFF asked tuso for this and tuso serves no such path.
     */
    List<ShiftEntity> findByTenantIdAndFacilityIdAndStartTimeBetweenOrderByStartTimeAsc(
            UUID tenantId, UUID facilityId, OffsetDateTime from, OffsetDateTime to);

    /**
     * On-call rota read (V009): the same window, restricted to shifts that play a rota role.
     * An ordinary rostered shift carries no on_call_role and is not part of the rota.
     */
    @Query("SELECT s FROM ShiftEntity s WHERE s.tenantId = :tenantId AND s.facilityId = :facilityId "
            + "AND s.onCallRole IS NOT NULL AND s.startTime >= :from AND s.startTime < :to "
            + "ORDER BY s.startTime ASC, s.onCallRole ASC")
    List<ShiftEntity> findOnCallForFacilityWindow(@Param("tenantId") UUID tenantId,
                                                  @Param("facilityId") UUID facilityId,
                                                  @Param("from") OffsetDateTime from,
                                                  @Param("to") OffsetDateTime to);
}
