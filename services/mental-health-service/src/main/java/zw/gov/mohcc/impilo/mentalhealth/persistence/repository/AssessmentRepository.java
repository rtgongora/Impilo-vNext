package zw.gov.mohcc.impilo.mentalhealth.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.AssessmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<AssessmentEntity, UUID> {
    Optional<AssessmentEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<AssessmentEntity> findByTenantIdAndReferralIdOrderByAssessedAtDesc(UUID tenantId, UUID referralId);
}
