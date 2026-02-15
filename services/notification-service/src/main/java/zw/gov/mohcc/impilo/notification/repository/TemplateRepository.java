package zw.gov.mohcc.impilo.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.notification.domain.TemplateEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateRepository extends JpaRepository<TemplateEntity, String> {

    List<TemplateEntity> findByTenantId(String tenantId);

    Optional<TemplateEntity> findByKey(String key);
}
