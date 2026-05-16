package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryStatusEventEntity;

import java.util.List;
import java.util.UUID;

public interface DeliveryStatusEventRepository extends JpaRepository<DeliveryStatusEventEntity, Long> {
    List<DeliveryStatusEventEntity> findByDeliveryIdOrderByCreatedAtAsc(UUID deliveryId);
}
