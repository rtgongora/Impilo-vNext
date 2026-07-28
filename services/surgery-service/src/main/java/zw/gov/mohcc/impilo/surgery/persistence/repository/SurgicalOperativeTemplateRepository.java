package zw.gov.mohcc.impilo.surgery.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalOperativeTemplateEntity;

import java.util.List;
import java.util.UUID;

public interface SurgicalOperativeTemplateRepository extends JpaRepository<SurgicalOperativeTemplateEntity, UUID> {

    List<SurgicalOperativeTemplateEntity> findByTenantIdAndSpecialtyOrderByTemplateCodeAsc(
            UUID tenantId, String specialty);
}
