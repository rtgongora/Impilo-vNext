package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryNotificationEventEntity;

import java.util.List;
import java.util.UUID;

public interface DeliveryNotificationEventRepository extends JpaRepository<DeliveryNotificationEventEntity, Long> {
    List<DeliveryNotificationEventEntity> findByDeliveryIdOrderByDispatchedAtDesc(UUID deliveryId);
}
