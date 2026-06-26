package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.CohortMembershipEntity;

public interface CohortMembershipRepository extends JpaRepository<CohortMembershipEntity, UUID> {

    List<CohortMembershipEntity> findByTenantIdAndCohortId(UUID tenantId, UUID cohortId);

    Optional<CohortMembershipEntity> findByCohortIdAndSubjectTypeAndSubjectId(
            UUID cohortId, String subjectType, String subjectId);
}
