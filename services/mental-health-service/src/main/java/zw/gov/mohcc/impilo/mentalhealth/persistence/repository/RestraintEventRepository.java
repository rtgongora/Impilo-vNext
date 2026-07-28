package zw.gov.mohcc.impilo.mentalhealth.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.RestraintEventEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestraintEventRepository extends JpaRepository<RestraintEventEntity, UUID> {
    Optional<RestraintEventEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<RestraintEventEntity> findByTenantIdAndReferralIdOrderByInitiatedAtDesc(UUID tenantId, UUID referralId);
    List<RestraintEventEntity> findByTenantIdAndReviewedAtIsNullOrderByInitiatedAtAsc(UUID tenantId);
}
