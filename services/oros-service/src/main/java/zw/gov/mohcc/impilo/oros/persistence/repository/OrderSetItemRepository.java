package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderSetItemEntity;

import java.util.List;
import java.util.UUID;

public interface OrderSetItemRepository extends JpaRepository<OrderSetItemEntity, UUID> {
    List<OrderSetItemEntity> findBySetIdOrderBySortOrderAsc(UUID setId);
}
