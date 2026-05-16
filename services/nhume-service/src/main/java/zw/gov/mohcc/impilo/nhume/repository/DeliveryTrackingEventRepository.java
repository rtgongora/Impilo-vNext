package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryTrackingEventEntity;

import java.util.List;
import java.util.UUID;

public interface DeliveryTrackingEventRepository extends JpaRepository<DeliveryTrackingEventEntity, Long> {
    List<DeliveryTrackingEventEntity> findByDeliveryIdOrderByCapturedAtDesc(UUID deliveryId);
    Page<DeliveryTrackingEventEntity> findByDeliveryIdOrderByCapturedAtDesc(UUID deliveryId, Pageable pageable);
}
