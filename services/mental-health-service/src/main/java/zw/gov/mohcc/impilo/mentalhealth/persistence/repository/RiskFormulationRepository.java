package zw.gov.mohcc.impilo.mentalhealth.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.RiskFormulationEntity;

import java.util.List;
import java.util.UUID;

public interface RiskFormulationRepository extends JpaRepository<RiskFormulationEntity, UUID> {
    List<RiskFormulationEntity> findByTenantIdAndAssessmentIdOrderByFormulatedAtDesc(UUID tenantId, UUID assessmentId);
}
