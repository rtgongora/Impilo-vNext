package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.ContraceptiveAdministrationEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContraceptiveAdministrationRepository
        extends JpaRepository<ContraceptiveAdministrationEntity, UUID> {

    @Query("SELECT a FROM ContraceptiveAdministrationEntity a "
            + "WHERE a.contraceptiveEpisodeId = :episodeId "
            + "ORDER BY a.administeredOn DESC, a.recordedAt DESC")
    List<ContraceptiveAdministrationEntity> findForEpisode(@Param("episodeId") UUID episodeId);

    Optional<ContraceptiveAdministrationEntity> findByTenantIdAndClientOfflineId(UUID tenantId,
                                                                                 String clientOfflineId);

    /**
     * Doses that were due by a date and have no later act recorded — the defaulter list.
     *
     * <p>Written as "due on or before, with nothing since" rather than as a status flag, because a
     * flag would need updating by something and the thing that would update it is the visit that did
     * not happen. A woman defaults precisely by nobody touching her record.
     */
    @Query("SELECT a FROM ContraceptiveAdministrationEntity a "
            + "WHERE a.tenantId = :tenantId AND a.nextDueOn IS NOT NULL AND a.nextDueOn <= :asOf "
            + "AND NOT EXISTS (SELECT 1 FROM ContraceptiveAdministrationEntity later "
            + "                WHERE later.contraceptiveEpisodeId = a.contraceptiveEpisodeId "
            + "                AND later.administeredOn > a.administeredOn) "
            + "ORDER BY a.nextDueOn")
    List<ContraceptiveAdministrationEntity> findOverdue(@Param("tenantId") UUID tenantId,
                                                        @Param("asOf") LocalDate asOf);

    /**
     * Removals that left something behind.
     *
     * <p>A worklist, not a report. Every row is a woman carrying a device fragment who needs a
     * follow-up somebody has to arrange.
     */
    @Query("SELECT a FROM ContraceptiveAdministrationEntity a "
            + "WHERE a.tenantId = :tenantId AND a.retainedFragment = TRUE "
            + "ORDER BY a.administeredOn DESC")
    List<ContraceptiveAdministrationEntity> findRetainedFragments(@Param("tenantId") UUID tenantId);
}
