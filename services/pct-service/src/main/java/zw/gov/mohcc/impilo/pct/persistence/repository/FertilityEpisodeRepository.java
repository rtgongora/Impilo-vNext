package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.FertilityEpisodeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FertilityEpisodeRepository extends JpaRepository<FertilityEpisodeEntity, UUID> {

    @Query("SELECT f FROM FertilityEpisodeEntity f "
            + "WHERE f.tenantId = :tenantId AND f.subjectCpid = :subjectCpid "
            + "AND f.status IN ('ACTIVE', 'REFERRED')")
    Optional<FertilityEpisodeEntity> findOpen(@Param("tenantId") UUID tenantId,
                                              @Param("subjectCpid") String subjectCpid);

    @Query("SELECT f FROM FertilityEpisodeEntity f "
            + "WHERE f.tenantId = :tenantId AND f.subjectCpid = :subjectCpid "
            + "ORDER BY f.openedOn DESC")
    List<FertilityEpisodeEntity> findBySubject(@Param("tenantId") UUID tenantId,
                                               @Param("subjectCpid") String subjectCpid);

    Optional<FertilityEpisodeEntity> findByTenantIdAndClientOfflineId(UUID tenantId,
                                                                      String clientOfflineId);

    /**
     * Couples investigated for infertility where the male partner was not assessed.
     *
     * <p>The audit this table was shaped to make answerable. The finding is the count, and it is
     * usually uncomfortable.
     */
    @Query("SELECT f FROM FertilityEpisodeEntity f "
            + "WHERE f.tenantId = :tenantId "
            + "AND f.femaleFactorAssessed = 'ASSESSED' "
            + "AND (f.maleFactorAssessed IS NULL OR f.maleFactorAssessed <> 'ASSESSED') "
            + "ORDER BY f.openedOn DESC")
    List<FertilityEpisodeEntity> findWomanAssessedButNotMan(@Param("tenantId") UUID tenantId);
}
