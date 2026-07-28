package zw.gov.mohcc.impilo.mentalhealth.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.SafetyPlanEntity;

import java.util.List;
import java.util.UUID;

public interface SafetyPlanRepository extends JpaRepository<SafetyPlanEntity, UUID> {
    List<SafetyPlanEntity> findByTenantIdAndReferralIdOrderByCreatedAtDesc(UUID tenantId, UUID referralId);
}
