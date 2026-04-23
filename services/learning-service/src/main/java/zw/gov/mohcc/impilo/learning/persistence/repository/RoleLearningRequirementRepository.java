package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.RoleLearningRequirementEntity;

public interface RoleLearningRequirementRepository extends JpaRepository<RoleLearningRequirementEntity, UUID> {

    List<RoleLearningRequirementEntity> findByTenantIdAndActiveTrueAndRoleCodeIn(UUID tenantId, List<String> roleCodes);
}
