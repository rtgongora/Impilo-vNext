package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ClientHomeStockEntity;

import java.util.List;
import java.util.UUID;

/**
 * Repository for client household stock.
 */
@Repository
public interface ClientHomeStockRepository extends JpaRepository<ClientHomeStockEntity, UUID> {

    List<ClientHomeStockEntity> findByTenantIdAndClientIdOrderByItemName(UUID tenantId, String clientId);
}
