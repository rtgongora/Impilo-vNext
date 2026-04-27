package zw.gov.mohcc.impilo.mvumo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentTemplateRepository extends JpaRepository<ConsentTemplateEntity, UUID> {

    List<ConsentTemplateEntity> findByTenantIdAndRetiredAtIsNullOrderByTemplateKeyAscVersionDesc(
            UUID tenantId);

    Optional<ConsentTemplateEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
