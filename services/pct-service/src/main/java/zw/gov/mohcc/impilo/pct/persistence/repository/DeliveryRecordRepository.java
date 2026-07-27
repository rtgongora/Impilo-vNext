package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeliveryRecordEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRecordRepository extends JpaRepository<DeliveryRecordEntity, UUID> {

    /**
     * The delivery for a pregnancy, if one is recorded. A partial unique index guarantees at most
     * one non-error row, so this is a lookup rather than a best guess over several.
     */
    @Query("SELECT d FROM DeliveryRecordEntity d "
            + "WHERE d.tenantId = :tenantId AND d.pregnancyEpisodeId = :episodeId "
            + "AND d.status <> 'ENTERED_IN_ERROR'")
    Optional<DeliveryRecordEntity> findForPregnancy(@Param("tenantId") UUID tenantId,
                                                    @Param("episodeId") UUID episodeId);

    @Query("SELECT d FROM DeliveryRecordEntity d "
            + "WHERE d.tenantId = :tenantId AND d.motherCpid = :motherCpid "
            + "ORDER BY d.deliveredAt DESC")
    List<DeliveryRecordEntity> findByMother(@Param("tenantId") UUID tenantId,
                                            @Param("motherCpid") String motherCpid);

    /** Resolves a replayed offline packet to the delivery it already recorded. */
    Optional<DeliveryRecordEntity> findByTenantIdAndClientOfflineId(UUID tenantId, String clientOfflineId);
}
