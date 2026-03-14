package zw.gov.mohcc.impilo.integration.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.integration.domain.MappingTemplateEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface MappingTemplateRepository extends JpaRepository<MappingTemplateEntity, String> {

    List<MappingTemplateEntity> findByTenantIdAndActive(String tenantId, boolean active);

    Optional<MappingTemplateEntity> findByNameAndTenantId(String name, String tenantId);

    List<MappingTemplateEntity> findByTenantId(String tenantId);
}
