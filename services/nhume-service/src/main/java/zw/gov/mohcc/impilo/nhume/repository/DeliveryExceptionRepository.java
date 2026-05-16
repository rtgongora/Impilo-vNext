package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryExceptionEntity;

import java.util.List;
import java.util.UUID;

public interface DeliveryExceptionRepository extends JpaRepository<DeliveryExceptionEntity, UUID> {
    List<DeliveryExceptionEntity> findByDeliveryIdOrderByRaisedAtDesc(UUID deliveryId);
    long countByResolvedAtIsNull();
}
