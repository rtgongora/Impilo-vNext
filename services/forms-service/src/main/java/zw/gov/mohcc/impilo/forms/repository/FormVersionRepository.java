package zw.gov.mohcc.impilo.forms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.forms.domain.FormVersionEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface FormVersionRepository extends JpaRepository<FormVersionEntity, String> {

    List<FormVersionEntity> findByFormIdOrderByVersionNumberDesc(String formId);

    Optional<FormVersionEntity> findTopByFormIdOrderByVersionNumberDesc(String formId);

    Optional<FormVersionEntity> findByFormIdAndVersionNumber(String formId, int versionNumber);
}
