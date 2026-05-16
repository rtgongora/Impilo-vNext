package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryAuditEventEntity;

import java.util.List;
import java.util.UUID;

public interface DeliveryAuditEventRepository extends JpaRepository<DeliveryAuditEventEntity, Long> {
    List<DeliveryAuditEventEntity> findByDeliveryIdOrderByCreatedAtDesc(UUID deliveryId);
}
