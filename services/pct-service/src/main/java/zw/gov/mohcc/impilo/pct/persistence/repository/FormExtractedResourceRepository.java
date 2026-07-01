package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormExtractedResourceEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface FormExtractedResourceRepository extends JpaRepository<FormExtractedResourceEntity, UUID> {

    List<FormExtractedResourceEntity> findByResponseId(UUID responseId);

    List<FormExtractedResourceEntity> findByStatusIn(List<String> statuses);
}
