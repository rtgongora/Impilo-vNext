package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.domain.RefillStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RefillRequestEntity;

import java.util.List;
import java.util.UUID;

/**
 * Repository for client refill requests.
 */
@Repository
public interface RefillRequestRepository extends JpaRepository<RefillRequestEntity, UUID> {

    List<RefillRequestEntity> findByTenantIdAndClientIdOrderByCreatedAtDesc(UUID tenantId, String clientId);

    List<RefillRequestEntity> findByTenantIdAndClientIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, String clientId, RefillStatus status);
}
