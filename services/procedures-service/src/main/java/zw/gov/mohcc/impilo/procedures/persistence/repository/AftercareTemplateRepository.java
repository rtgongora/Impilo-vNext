package zw.gov.mohcc.impilo.procedures.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procedures.persistence.entity.AftercareTemplateEntity;

import java.util.Optional;
import java.util.UUID;

public interface AftercareTemplateRepository extends JpaRepository<AftercareTemplateEntity, UUID> {
    Optional<AftercareTemplateEntity> findByTenantIdAndTemplateCodeAndStatus(
            UUID tenantId, String templateCode, String status);
}
