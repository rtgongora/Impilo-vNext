package zw.gov.mohcc.impilo.scheduling.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.SurgicalWaitlistEntryEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SurgicalWaitlistRepository extends JpaRepository<SurgicalWaitlistEntryEntity, UUID> {

    Optional<SurgicalWaitlistEntryEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<SurgicalWaitlistEntryEntity> findByTenantIdAndReferralId(UUID tenantId, UUID referralId);

    List<SurgicalWaitlistEntryEntity> findByTenantIdAndStatusOrderByClinicalPriorityAscTargetDateAsc(
            UUID tenantId, String status);

    List<SurgicalWaitlistEntryEntity> findByTenantIdOrderByClinicalPriorityAscTargetDateAsc(UUID tenantId);
}
