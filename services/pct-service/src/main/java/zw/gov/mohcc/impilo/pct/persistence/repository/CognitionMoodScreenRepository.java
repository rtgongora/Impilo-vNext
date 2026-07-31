package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.CognitionMoodScreenEntity;

import java.util.List;
import java.util.UUID;

public interface CognitionMoodScreenRepository extends JpaRepository<CognitionMoodScreenEntity, UUID> {
    List<CognitionMoodScreenEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);
}
