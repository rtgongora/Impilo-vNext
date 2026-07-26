package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemLinkEntity;

import java.util.List;
import java.util.UUID;

public interface ProblemLinkRepository extends JpaRepository<ProblemLinkEntity, UUID> {

    List<ProblemLinkEntity> findByTenantIdAndProblemIdOrderByCreatedAtDesc(UUID tenantId, UUID problemId);

    /** Reverse lookup: what does this observation support, what is this medicine treating. */
    List<ProblemLinkEntity> findByTenantIdAndTargetTypeAndTargetRef(
            UUID tenantId, String targetType, String targetRef);
}
