package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.SystemsReviewEntity;

import java.util.List;
import java.util.UUID;

public interface SystemsReviewRepository extends JpaRepository<SystemsReviewEntity, UUID> {
    List<SystemsReviewEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);
}
