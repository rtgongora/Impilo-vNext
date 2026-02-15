package zw.gov.mohcc.impilo.forms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.forms.domain.FormDefinitionEntity;

import java.util.List;

@Repository
public interface FormDefinitionRepository extends JpaRepository<FormDefinitionEntity, String> {

    List<FormDefinitionEntity> findByTenantId(String tenantId);

    List<FormDefinitionEntity> findByTenantIdAndStatus(String tenantId, String status);
}
