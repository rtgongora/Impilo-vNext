package zw.gov.mohcc.impilo.vashandi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.TrainingRequirementEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingRequirementRepository extends JpaRepository<TrainingRequirementEntity, UUID> {

    List<TrainingRequirementEntity> findByTenantIdOrderByRoleTemplateIdAscCourseCodeAsc(UUID tenantId);

    List<TrainingRequirementEntity> findByTenantIdAndRoleTemplateIdAndActiveTrue(UUID tenantId, String roleTemplateId);

    Optional<TrainingRequirementEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<TrainingRequirementEntity> findByTenantIdAndRoleTemplateIdAndCourseCode(
            UUID tenantId, String roleTemplateId, String courseCode);
}
