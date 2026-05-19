package zw.gov.mohcc.impilo.dispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.dispatch.domain.DeliveryAssignmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignmentEntity, UUID> {
    List<DeliveryAssignmentEntity> findByDeliveryIdOrderByAssignedAtDesc(UUID deliveryId);
    Optional<DeliveryAssignmentEntity> findFirstByDeliveryIdOrderByAssignedAtDesc(UUID deliveryId);
}
