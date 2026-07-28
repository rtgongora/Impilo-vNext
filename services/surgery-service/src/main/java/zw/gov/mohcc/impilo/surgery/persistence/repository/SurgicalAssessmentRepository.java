package zw.gov.mohcc.impilo.surgery.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalAssessmentEntity;

import java.util.Optional;
import java.util.UUID;

public interface SurgicalAssessmentRepository extends JpaRepository<SurgicalAssessmentEntity, UUID> {

    Optional<SurgicalAssessmentEntity> findBySurgicalEpisodeIdAndTenantId(UUID surgicalEpisodeId, UUID tenantId);
}
