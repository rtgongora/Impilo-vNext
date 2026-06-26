package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.CohortFacilitatorEntity;

public interface CohortFacilitatorRepository extends JpaRepository<CohortFacilitatorEntity, UUID> {

    List<CohortFacilitatorEntity> findByTenantIdAndCohortId(UUID tenantId, UUID cohortId);

    List<CohortFacilitatorEntity> findByTenantIdAndFacilitatorId(UUID tenantId, UUID facilitatorId);
}
