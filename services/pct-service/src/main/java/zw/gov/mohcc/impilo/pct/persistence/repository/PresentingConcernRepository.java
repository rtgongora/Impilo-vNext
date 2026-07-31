package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.PresentingConcernEntity;

import java.util.List;
import java.util.UUID;

public interface PresentingConcernRepository extends JpaRepository<PresentingConcernEntity, UUID> {
    List<PresentingConcernEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);
}
