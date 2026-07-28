package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.MdtDecisionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MdtDecisionRepository extends JpaRepository<MdtDecisionEntity, UUID> {

    List<MdtDecisionEntity> findByTenantIdAndSubjectCpidOrderByMetOnDesc(UUID tenantId, String subjectCpid);

    Optional<MdtDecisionEntity> findByTenantIdAndDecisionId(UUID tenantId, UUID decisionId);
}
