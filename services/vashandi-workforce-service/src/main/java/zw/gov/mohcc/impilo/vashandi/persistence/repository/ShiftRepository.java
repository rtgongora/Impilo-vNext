package zw.gov.mohcc.impilo.vashandi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ShiftEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<ShiftEntity, UUID> {

    List<ShiftEntity> findByTenantIdAndRosterIdOrderByStartTimeAsc(UUID tenantId, UUID rosterId);

    Optional<ShiftEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ShiftEntity> findByTenantIdAndWorkforceProfileIdAndStartTimeBetweenOrderByStartTimeAsc(
            UUID tenantId, UUID workforceProfileId, OffsetDateTime start, OffsetDateTime end);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    long countByTenantIdAndFacilityIdAndStatus(UUID tenantId, UUID facilityId, String status);
}
