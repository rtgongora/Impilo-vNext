package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.GatewayTransactionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GatewayTransactionRepository extends JpaRepository<GatewayTransactionEntity, UUID> {
    List<GatewayTransactionEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);
    Optional<GatewayTransactionEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
