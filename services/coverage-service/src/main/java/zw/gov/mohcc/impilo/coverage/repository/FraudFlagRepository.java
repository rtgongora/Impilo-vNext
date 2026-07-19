package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.FraudFlagEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FraudFlagRepository extends JpaRepository<FraudFlagEntity, UUID> {
    List<FraudFlagEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);
    List<FraudFlagEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<FraudFlagEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
