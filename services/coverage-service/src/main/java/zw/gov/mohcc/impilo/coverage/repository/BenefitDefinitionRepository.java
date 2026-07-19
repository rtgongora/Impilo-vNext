package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.BenefitDefinitionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BenefitDefinitionRepository extends JpaRepository<BenefitDefinitionEntity, UUID> {
    List<BenefitDefinitionEntity> findByTenantIdAndPlanVersionId(UUID tenantId, UUID planVersionId);
    Optional<BenefitDefinitionEntity> findByTenantIdAndPlanVersionIdAndBenefitCode(
            UUID tenantId, UUID planVersionId, String benefitCode);
    Optional<BenefitDefinitionEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
