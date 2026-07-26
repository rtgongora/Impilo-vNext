package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.ContraceptiveEpisodeEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContraceptiveEpisodeRepository
        extends JpaRepository<ContraceptiveEpisodeEntity, UUID> {

    /**
     * Everything she is currently using. Plural on purpose — dual method use is normal, and a
     * caller that expected one answer would silently drop the second.
     */
    @Query("SELECT e FROM ContraceptiveEpisodeEntity e "
            + "WHERE e.tenantId = :tenantId AND e.subjectCpid = :subjectCpid "
            + "AND e.status IN ('PLANNED', 'ACTIVE') "
            + "ORDER BY e.startedOn DESC")
    List<ContraceptiveEpisodeEntity> findCurrent(@Param("tenantId") UUID tenantId,
                                                 @Param("subjectCpid") String subjectCpid);

    /**
     * The current episode for one method. A partial unique index guarantees at most one, so this is
     * a lookup rather than a best guess.
     */
    @Query("SELECT e FROM ContraceptiveEpisodeEntity e "
            + "WHERE e.tenantId = :tenantId AND e.subjectCpid = :subjectCpid "
            + "AND e.methodCode = :methodCode AND e.status IN ('PLANNED', 'ACTIVE')")
    Optional<ContraceptiveEpisodeEntity> findCurrentForMethod(@Param("tenantId") UUID tenantId,
                                                              @Param("subjectCpid") String subjectCpid,
                                                              @Param("methodCode") String methodCode);

    @Query("SELECT e FROM ContraceptiveEpisodeEntity e "
            + "WHERE e.tenantId = :tenantId AND e.subjectCpid = :subjectCpid "
            + "ORDER BY e.startedOn DESC")
    List<ContraceptiveEpisodeEntity> findBySubject(@Param("tenantId") UUID tenantId,
                                                   @Param("subjectCpid") String subjectCpid);

    /** Resolves a replayed offline packet to the episode it already created. */
    Optional<ContraceptiveEpisodeEntity> findByTenantIdAndClientOfflineId(UUID tenantId,
                                                                          String clientOfflineId);

    /**
     * Methods whose protection is about to run out.
     *
     * <p>Bounded at both ends deliberately. An open-ended "expired" query returns every implant ever
     * placed and is unusable as a worklist, and a worklist nobody can work is one nobody opens.
     */
    @Query("SELECT e FROM ContraceptiveEpisodeEntity e "
            + "WHERE e.tenantId = :tenantId AND e.status = 'ACTIVE' "
            + "AND e.expectedEndOn IS NOT NULL "
            + "AND e.expectedEndOn BETWEEN :from AND :to "
            + "ORDER BY e.expectedEndOn")
    List<ContraceptiveEpisodeEntity> findExpiringBetween(@Param("tenantId") UUID tenantId,
                                                         @Param("from") LocalDate from,
                                                         @Param("to") LocalDate to);

    /**
     * Insertions that were deferred and are owed.
     *
     * <p>Exists so a deferral is a plan rather than a refusal. Without a query that can find these,
     * "come back when we can exclude pregnancy" is a sentence spoken once and never followed up.
     */
    @Query("SELECT e FROM ContraceptiveEpisodeEntity e "
            + "WHERE e.tenantId = :tenantId AND e.deferredInsertionOwed = TRUE "
            + "AND e.status IN ('PLANNED', 'ACTIVE') "
            + "ORDER BY e.startedOn")
    List<ContraceptiveEpisodeEntity> findDeferredInsertionsOwed(@Param("tenantId") UUID tenantId);
}
