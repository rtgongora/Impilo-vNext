package zw.gov.mohcc.impilo.procurement.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procurement.persistence.entity.GoodsReceivedEntity;

import java.util.List;
import java.util.UUID;

public interface GoodsReceivedRepository extends JpaRepository<GoodsReceivedEntity, UUID> {
    List<GoodsReceivedEntity> findByTenantIdOrderByReceivedAtDesc(UUID tenantId);
}
