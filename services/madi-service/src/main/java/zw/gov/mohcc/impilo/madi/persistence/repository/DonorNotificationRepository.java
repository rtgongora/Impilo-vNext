package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonorNotificationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DonorNotificationRepository extends JpaRepository<DonorNotificationEntity, Long> {
    Optional<DonorNotificationEntity> findByNotificationIdAndTenantId(UUID notificationId, UUID tenantId);
    List<DonorNotificationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
