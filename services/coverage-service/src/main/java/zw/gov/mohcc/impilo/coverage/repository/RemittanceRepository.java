package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.RemittanceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RemittanceRepository extends JpaRepository<RemittanceEntity, UUID> {

    List<RemittanceEntity> findByTenantIdAndPayerId(UUID tenantId, String payerId);

    Optional<RemittanceEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
