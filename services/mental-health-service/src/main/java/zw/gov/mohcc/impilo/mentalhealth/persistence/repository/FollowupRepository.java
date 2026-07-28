package zw.gov.mohcc.impilo.mentalhealth.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.FollowupEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowupRepository extends JpaRepository<FollowupEntity, UUID> {
    Optional<FollowupEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<FollowupEntity> findByTenantIdAndReferralIdOrderByScheduledAtDesc(UUID tenantId, UUID referralId);
}
