package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyLossRecordEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PregnancyLossRecordRepository extends JpaRepository<PregnancyLossRecordEntity, UUID> {

    @Query("SELECT l FROM PregnancyLossRecordEntity l "
            + "WHERE l.tenantId = :tenantId AND l.motherCpid = :motherCpid "
            + "ORDER BY l.occurredOn DESC")
    List<PregnancyLossRecordEntity> findByMother(@Param("tenantId") UUID tenantId,
                                                 @Param("motherCpid") String motherCpid);

    @Query("SELECT l FROM PregnancyLossRecordEntity l "
            + "WHERE l.tenantId = :tenantId AND l.pregnancyEpisodeId = :episodeId")
    List<PregnancyLossRecordEntity> findByEpisode(@Param("tenantId") UUID tenantId,
                                                  @Param("episodeId") UUID episodeId);

    Optional<PregnancyLossRecordEntity> findByTenantIdAndClientOfflineId(UUID tenantId, String clientOfflineId);
}
