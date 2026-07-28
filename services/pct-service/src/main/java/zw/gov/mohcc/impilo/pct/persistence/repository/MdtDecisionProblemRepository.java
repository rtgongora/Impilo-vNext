package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.MdtDecisionProblemEntity;

import java.util.List;
import java.util.UUID;

public interface MdtDecisionProblemRepository
        extends JpaRepository<MdtDecisionProblemEntity, MdtDecisionProblemEntity.Key> {

    List<MdtDecisionProblemEntity> findByTenantIdAndDecisionId(UUID tenantId, UUID decisionId);
}
