package zw.gov.mohcc.impilo.pharmacy.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseItemStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseItemEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface DispenseItemRepository extends JpaRepository<DispenseItemEntity, UUID> {

    List<DispenseItemEntity> findByDispenseOrderIdOrderByCreatedAtAsc(UUID dispenseOrderId);

    List<DispenseItemEntity> findByDispenseOrderIdAndStatus(UUID dispenseOrderId, DispenseItemStatus status);
}
