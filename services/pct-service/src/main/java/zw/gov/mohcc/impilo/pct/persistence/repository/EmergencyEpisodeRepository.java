package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmergencyEpisodeRepository extends JpaRepository<EmergencyEpisodeEntity, UUID> {

    Optional<EmergencyEpisodeEntity> findByEpisodeIdAndTenantId(UUID episodeId, UUID tenantId);

    /** The idempotent-mint lookup. Mirrors uq_emergency_episode_entry_source. */
    Optional<EmergencyEpisodeEntity> findByTenantIdAndEntryRouteAndEntrySourceRef(
            UUID tenantId, String entryRoute, String entrySourceRef);

    /** The operational board. */
    List<EmergencyEpisodeEntity> findByTenantIdAndFacilityIdAndStateInOrderByArrivedAtDesc(
            UUID tenantId, UUID facilityId, List<String> states);

    /**
     * Every open episode, across all tenants — the alert sweep's input (W5).
     *
     * <p>Deliberately not tenant-scoped: the sweep is a system job with no TrustContext, and an
     * episode that alerts only when someone happens to be looking at its tenant is not a safety net.
     * Tenant isolation is preserved where it matters — every alert it writes carries the episode's
     * own {@code tenant_id}, and every read path is tenant-scoped.
     */
    List<EmergencyEpisodeEntity> findByStateIn(List<String> states);

    /**
     * Repoint a provisional identity onto a confirmed CPID after a VITO merge.
     *
     * <p>This updates an attribute. It never re-parents a row, because every emergency clinical
     * record anchors on {@code episode_id} rather than on the cpid — which is what makes a merge
     * incapable of losing orders, results, medicines, procedures or notes.
     */
    // The timestamps are BOUND, not CURRENT_TIMESTAMP. JPQL's CURRENT_TIMESTAMP resolves to
    // java.sql.Timestamp, which Hibernate 6 refuses to assign to an OffsetDateTime path — and it
    // does so at CONTEXT STARTUP, when Spring Data validates the @Query. Under the H2 dialect used
    // by the test profile that made the entire pct Spring context unbootable, so no @SpringBootTest
    // could run in this service at all. It survived because the six that exist are named *IT and
    // are excluded from surefire: the context could not boot, and nothing was trying to boot it.
    //
    // Postgres accepts it at runtime, which is why the deployed service is healthy — the defect was
    // never in production, only in the ability to test.
    //
    // Binding the instant also makes the update assertable: a test can pass a known time and check
    // it landed, instead of hoping the database agreed.
    @Modifying
    @Query("update EmergencyEpisodeEntity e set e.subjectCpid = :confirmed, "
            + "e.identityMode = 'KNOWN', e.identityResolvedAt = :now, "
            + "e.updatedAt = :now "
            + "where e.tenantId = :tenantId and e.subjectCpid = :provisional")
    int repointSubjectCpid(@Param("tenantId") UUID tenantId,
                           @Param("provisional") String provisional,
                           @Param("confirmed") String confirmed,
                           @Param("now") java.time.OffsetDateTime now);
}
