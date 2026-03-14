package zw.gov.mohcc.impilo.ubomi.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.BirthNotificationEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BirthNotificationRepository extends JpaRepository<BirthNotificationEntity, Long> {

    Page<BirthNotificationEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<BirthNotificationEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    Page<BirthNotificationEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId, Pageable pageable);

    Optional<BirthNotificationEntity> findByTenantIdAndNotificationNumber(UUID tenantId, String notificationNumber);

    Optional<BirthNotificationEntity> findByTenantIdAndId(UUID tenantId, Long id);
}
