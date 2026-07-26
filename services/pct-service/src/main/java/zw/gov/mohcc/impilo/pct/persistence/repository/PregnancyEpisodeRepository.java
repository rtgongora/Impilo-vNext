package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyEpisodeEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PregnancyEpisodeRepository extends JpaRepository<PregnancyEpisodeEntity, UUID> {

    /**
     * The woman's current pregnancy, if she has one.
     *
     * <p>A partial unique index guarantees at most one, so this is a lookup rather than a
     * best-guess over several candidates.
     */
    @Query("SELECT p FROM PregnancyEpisodeEntity p "
            + "WHERE p.tenantId = :tenantId AND p.subjectCpid = :subjectCpid "
            + "AND p.status = 'ONGOING'")
    Optional<PregnancyEpisodeEntity> findOngoing(@Param("tenantId") UUID tenantId,
                                                 @Param("subjectCpid") String subjectCpid);

    @Query("SELECT p FROM PregnancyEpisodeEntity p "
            + "WHERE p.tenantId = :tenantId AND p.subjectCpid = :subjectCpid "
            + "ORDER BY p.pregnancyStartDate DESC")
    List<PregnancyEpisodeEntity> findBySubject(@Param("tenantId") UUID tenantId,
                                               @Param("subjectCpid") String subjectCpid);

    /** Idempotent replay of a store-and-forward packet (the V050 precedent). */
    Optional<PregnancyEpisodeEntity> findByTenantIdAndClientOfflineId(UUID tenantId, String clientOfflineId);

    /**
     * The pregnancy that was current on a given date — how a labour observation or a birth
     * recorded after the fact finds the episode it belongs to.
     *
     * <p>Bounded at the end by a grace period rather than by {@code ended_on} exactly, because a
     * delivery is routinely recorded minutes to days after it happened.
     */
    @Query("SELECT p FROM PregnancyEpisodeEntity p "
            + "WHERE p.tenantId = :tenantId AND p.subjectCpid = :subjectCpid "
            + "AND p.status <> 'ENTERED_IN_ERROR' "
            + "AND p.pregnancyStartDate <= :on "
            + "AND (p.endedOn IS NULL OR p.endedOn >= :graceFloor) "
            + "ORDER BY p.pregnancyStartDate DESC")
    List<PregnancyEpisodeEntity> findCoveringDate(@Param("tenantId") UUID tenantId,
                                                  @Param("subjectCpid") String subjectCpid,
                                                  @Param("on") LocalDate on,
                                                  @Param("graceFloor") LocalDate graceFloor);
}
