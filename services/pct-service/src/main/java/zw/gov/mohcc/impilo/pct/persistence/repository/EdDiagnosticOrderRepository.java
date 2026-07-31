package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.EdDiagnosticOrderEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EdDiagnosticOrderRepository extends JpaRepository<EdDiagnosticOrderEntity, UUID> {
    Optional<EdDiagnosticOrderEntity> findByTenantIdAndOrosOrderId(UUID tenantId, String orosOrderId);

    List<EdDiagnosticOrderEntity> findByVisitIdOrderByCreatedAtDesc(UUID visitId);
}
