package zw.gov.mohcc.impilo.procedures.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procedures.persistence.entity.SafetyPauseTemplateEntity;

import java.util.Optional;
import java.util.UUID;

public interface SafetyPauseTemplateRepository extends JpaRepository<SafetyPauseTemplateEntity, UUID> {
    Optional<SafetyPauseTemplateEntity> findByTenantIdAndTemplateCodeAndStatus(
            UUID tenantId, String templateCode, String status);
}
