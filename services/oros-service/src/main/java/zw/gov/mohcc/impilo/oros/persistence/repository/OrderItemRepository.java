package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderItemEntity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> {

    List<OrderItemEntity> findByOrderId(String orderId);

    /** Batch form — enriching an order list one order at a time is an N+1 on every EHR results tab. */
    List<OrderItemEntity> findByOrderIdIn(Collection<String> orderIds);

    /** Remove all items for an order (used when replacing a draft's items on update). */
    void deleteByOrderId(String orderId);
}
