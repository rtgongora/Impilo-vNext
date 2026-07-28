package zw.gov.mohcc.impilo.pct.persistence.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyAlertEntity;

public interface EmergencyAlertRepository extends JpaRepository<EmergencyAlertEntity, UUID> {

    /**
     * The open-alert states, mirroring the {@code uq_emergency_alert_open} partial unique index.
     * Kept in one place so the application's idea of "open" cannot drift from the database's.
     */
    List<String> OPEN_STATUSES = List.of("RAISED", "ACKNOWLEDGED", "OVERDUE");

    Optional<EmergencyAlertEntity> findByAlertIdAndTenantId(UUID alertId, UUID tenantId);

    /**
     * Pre-check for the anti-noise rule. The partial unique index is the real guarantee; this exists
     * so the ordinary path does not rely on catching a constraint violation.
     */
    @Query("select a from EmergencyAlertEntity a where a.tenantId = :tenantId "
            + "and a.episodeId = :episodeId and a.alertType = :alertType "
            + "and a.status in ('RAISED','ACKNOWLEDGED','OVERDUE')")
    Optional<EmergencyAlertEntity> findOpen(@Param("tenantId") UUID tenantId,
                                            @Param("episodeId") UUID episodeId,
                                            @Param("alertType") String alertType);

    /** Responder board: open alerts for a facility, most urgent clock first. */
    @Query("select a from EmergencyAlertEntity a where a.tenantId = :tenantId "
            + "and (:facilityId is null or a.facilityId = :facilityId) "
            + "and a.status in ('RAISED','ACKNOWLEDGED','OVERDUE') "
            + "order by a.responseDueAt asc")
    List<EmergencyAlertEntity> findOpenForFacility(@Param("tenantId") UUID tenantId,
                                                   @Param("facilityId") UUID facilityId);

    List<EmergencyAlertEntity> findByTenantIdAndEpisodeIdOrderByRaisedAtDesc(UUID tenantId, UUID episodeId);

    /** Alerts past their response window that nobody has responded to yet. */
    @Query("select a from EmergencyAlertEntity a where a.status in ('RAISED','ACKNOWLEDGED') "
            + "and a.responseDueAt < :now")
    List<EmergencyAlertEntity> findBreached(@Param("now") OffsetDateTime now);
}
