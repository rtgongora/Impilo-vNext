package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.PayerEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayerRepository extends JpaRepository<PayerEntity, UUID> {

    List<PayerEntity> findByTenantId(UUID tenantId);

    List<PayerEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    Optional<PayerEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<PayerEntity> findByTenantIdAndPayerCode(UUID tenantId, String payerCode);
}
