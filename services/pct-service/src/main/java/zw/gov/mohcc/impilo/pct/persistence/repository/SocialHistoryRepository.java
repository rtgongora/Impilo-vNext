package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.SocialHistoryEntity;

import java.util.List;
import java.util.UUID;

public interface SocialHistoryRepository extends JpaRepository<SocialHistoryEntity, UUID> {

    List<SocialHistoryEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);
}
