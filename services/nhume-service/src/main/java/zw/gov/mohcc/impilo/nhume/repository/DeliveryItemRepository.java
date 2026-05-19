package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryItemEntity;

import java.util.List;
import java.util.UUID;

public interface DeliveryItemRepository extends JpaRepository<DeliveryItemEntity, UUID> {
    List<DeliveryItemEntity> findByDeliveryIdOrderBySequenceNoAsc(UUID deliveryId);
}
