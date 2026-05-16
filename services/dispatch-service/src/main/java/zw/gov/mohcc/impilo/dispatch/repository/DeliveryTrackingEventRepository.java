package zw.gov.mohcc.impilo.dispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.dispatch.domain.DeliveryTrackingEventEntity;

import java.util.List;
import java.util.UUID;

public interface DeliveryTrackingEventRepository extends JpaRepository<DeliveryTrackingEventEntity, Long> {
    List<DeliveryTrackingEventEntity> findByDeliveryIdOrderByCreatedAtAsc(UUID deliveryId);
}
