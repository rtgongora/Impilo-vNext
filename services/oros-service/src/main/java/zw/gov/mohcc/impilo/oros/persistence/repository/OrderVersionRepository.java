package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderVersionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderVersionRepository extends JpaRepository<OrderVersionEntity, String> {

    /** Head = highest version for the order. */
    Optional<OrderVersionEntity> findFirstByOrderIdOrderByVersionDesc(String orderId);

    List<OrderVersionEntity> findByTenantIdAndOrderIdOrderByVersionDesc(UUID tenantId, String orderId);

    Optional<OrderVersionEntity> findByTenantIdAndOrderVersionId(UUID tenantId, String orderVersionId);

    boolean existsByOrderId(String orderId);
}
