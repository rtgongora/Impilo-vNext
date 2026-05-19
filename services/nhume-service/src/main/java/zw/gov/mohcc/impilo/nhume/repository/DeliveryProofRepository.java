package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryProofEntity;

import java.util.List;
import java.util.UUID;

public interface DeliveryProofRepository extends JpaRepository<DeliveryProofEntity, UUID> {
    List<DeliveryProofEntity> findByDeliveryIdOrderByCapturedAtAsc(UUID deliveryId);
}
