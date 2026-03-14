package zw.gov.mohcc.impilo.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.notification.domain.TemplateVersionEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateVersionRepository extends JpaRepository<TemplateVersionEntity, String> {

    List<TemplateVersionEntity> findByTemplateIdOrderByVersionDesc(String templateId);

    Optional<TemplateVersionEntity> findByTemplateIdAndVersion(String templateId, int version);
}
