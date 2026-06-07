package zw.gov.mohcc.impilo.live.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LiveEventRepository extends JpaRepository<LiveEventEntity, UUID> {

    List<LiveEventEntity> findByTenantIdAndStatusOrderByStartTimeAsc(UUID tenantId, String status);

    List<LiveEventEntity> findByTenantIdAndContextTypeAndStatus(UUID tenantId, String contextType, String status);

    List<LiveEventEntity> findByTenantIdAndFacilityIdAndStatus(UUID tenantId, String facilityId, String status);

    Optional<LiveEventEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("select e from LiveEventEntity e where e.tenantId = :tenantId and e.status in :statuses order by e.startTime asc")
    List<LiveEventEntity> findDiscoverable(@Param("tenantId") UUID tenantId, @Param("statuses") List<String> statuses);

    List<LiveEventEntity> findByTenantIdAndLinkedFundoCourseId(UUID tenantId, UUID courseId);

    List<LiveEventEntity> findByTenantIdAndLinkedMadiDriveId(UUID tenantId, UUID driveId);
}
