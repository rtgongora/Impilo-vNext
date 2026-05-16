package zw.gov.mohcc.impilo.ndila.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ndila.domain.NdilaTrackingEventEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NdilaTrackingEventRepository extends JpaRepository<NdilaTrackingEventEntity, Long> {

    Optional<NdilaTrackingEventEntity> findFirstByTenantIdAndAssetIdOrderByEventTimestampDesc(
            UUID tenantId, String assetId);

    @Query("SELECT e FROM NdilaTrackingEventEntity e " +
            "WHERE e.tenantId = :tenantId AND e.assetId = :assetId " +
            "AND e.eventTimestamp >= :from AND e.eventTimestamp <= :to " +
            "ORDER BY e.eventTimestamp ASC")
    List<NdilaTrackingEventEntity> findHistory(@Param("tenantId") UUID tenantId,
                                               @Param("assetId") String assetId,
                                               @Param("from") OffsetDateTime from,
                                               @Param("to") OffsetDateTime to);

    @Query("SELECT e FROM NdilaTrackingEventEntity e " +
            "WHERE e.tenantId = :tenantId " +
            "AND e.id IN (SELECT MAX(e2.id) FROM NdilaTrackingEventEntity e2 " +
            "             WHERE e2.tenantId = :tenantId GROUP BY e2.assetId)")
    List<NdilaTrackingEventEntity> findLatestPerAsset(@Param("tenantId") UUID tenantId);
}
