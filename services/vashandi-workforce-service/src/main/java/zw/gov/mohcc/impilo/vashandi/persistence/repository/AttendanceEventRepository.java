package zw.gov.mohcc.impilo.vashandi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AttendanceEventEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEventEntity, UUID> {

    List<AttendanceEventEntity> findByTenantIdOrderByEventTimeDesc(UUID tenantId);

    List<AttendanceEventEntity> findByTenantIdAndWorkforceProfileIdOrderByEventTimeDesc(
            UUID tenantId, UUID workforceProfileId);

    List<AttendanceEventEntity> findByTenantIdAndEventTimeBetweenOrderByEventTimeDesc(
            UUID tenantId, OffsetDateTime start, OffsetDateTime end);

    long countByTenantIdAndEventType(UUID tenantId, String eventType);
}
