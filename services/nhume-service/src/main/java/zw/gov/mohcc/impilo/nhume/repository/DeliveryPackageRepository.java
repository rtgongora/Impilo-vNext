package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryPackageEntity;

import java.util.List;
import java.util.UUID;

public interface DeliveryPackageRepository extends JpaRepository<DeliveryPackageEntity, UUID> {
    List<DeliveryPackageEntity> findByDeliveryId(UUID deliveryId);
}
