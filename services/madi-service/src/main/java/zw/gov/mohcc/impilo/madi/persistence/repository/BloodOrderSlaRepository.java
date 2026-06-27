package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodOrderSlaEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodOrderSlaRepository extends JpaRepository<BloodOrderSlaEntity, UUID> {

    Optional<BloodOrderSlaEntity> findFirstByOrderIdAndStageAndStatus(UUID orderId, String stage, String status);

    List<BloodOrderSlaEntity> findByOrderId(UUID orderId);

    /** Open timers whose target has passed — the breach-scan input. */
    List<BloodOrderSlaEntity> findByStatusAndTargetAtBefore(String status, OffsetDateTime threshold);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
