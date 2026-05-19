package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryAssignmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignmentEntity, UUID> {

    List<DeliveryAssignmentEntity> findByDeliveryIdOrderByAssignedAtDesc(UUID deliveryId);

    Optional<DeliveryAssignmentEntity> findFirstByDeliveryIdAndSupersededAtIsNullOrderByAssignedAtDesc(UUID deliveryId);

    List<DeliveryAssignmentEntity> findByCourierIdAndSupersededAtIsNull(UUID courierId);

    List<DeliveryAssignmentEntity> findByAssetIdAndSupersededAtIsNull(UUID assetId);
}
