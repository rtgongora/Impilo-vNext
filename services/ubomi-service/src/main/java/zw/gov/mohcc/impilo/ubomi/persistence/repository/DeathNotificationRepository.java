package zw.gov.mohcc.impilo.ubomi.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.DeathNotificationEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeathNotificationRepository extends JpaRepository<DeathNotificationEntity, Long> {

    Page<DeathNotificationEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<DeathNotificationEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    Page<DeathNotificationEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId, Pageable pageable);

    Optional<DeathNotificationEntity> findByTenantIdAndNotificationNumber(UUID tenantId, String notificationNumber);

    Optional<DeathNotificationEntity> findByTenantIdAndId(UUID tenantId, Long id);
}
