package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.coverage.domain.ProviderContractEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderContractRepository extends JpaRepository<ProviderContractEntity, Long> {

    Optional<ProviderContractEntity> findByContractId(UUID contractId);

    @Query("""
            SELECT c FROM ProviderContractEntity c
            WHERE c.tenantId = :tenantId
              AND (:providerId IS NULL OR c.providerId = :providerId)
              AND (:payerId IS NULL OR c.payerId = :payerId)
              AND (:status IS NULL OR c.status = :status)
            ORDER BY c.createdAt DESC
            """)
    List<ProviderContractEntity> search(
            @Param("tenantId") UUID tenantId,
            @Param("providerId") String providerId,
            @Param("payerId") String payerId,
            @Param("status") String status);
}
