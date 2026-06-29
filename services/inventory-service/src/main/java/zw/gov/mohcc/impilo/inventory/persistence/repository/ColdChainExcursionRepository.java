package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.domain.ExcursionStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ColdChainExcursionEntity;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Dura cold-chain excursions.
 */
@Repository
public interface ColdChainExcursionRepository extends JpaRepository<ColdChainExcursionEntity, UUID> {

    List<ColdChainExcursionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<ColdChainExcursionEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, ExcursionStatus status);
}
