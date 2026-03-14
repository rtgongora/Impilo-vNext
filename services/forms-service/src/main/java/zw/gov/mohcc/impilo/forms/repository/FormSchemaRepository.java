package zw.gov.mohcc.impilo.forms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.forms.domain.FormSchemaEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface FormSchemaRepository extends JpaRepository<FormSchemaEntity, String> {

    List<FormSchemaEntity> findByTenantId(String tenantId);

    Optional<FormSchemaEntity> findByIdAndTenantId(String id, String tenantId);

    Optional<FormSchemaEntity> findByFormKeyAndTenantId(String formKey, String tenantId);
}
