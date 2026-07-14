package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<ReferralEntity, UUID> {
    Optional<ReferralEntity> findByTenantIdAndReferralId(UUID tenantId, UUID referralId);

    /** Kafka-consumer lookup (no TrustContext): referralId is globally unique (recording writeback, G20). */
    Optional<ReferralEntity> findByReferralId(UUID referralId);
    List<ReferralEntity> findByTenantIdAndPatientCpidOrderByCreatedAtDesc(UUID tenantId, String patientCpid);
    List<ReferralEntity> findByTenantIdAndFacilityIdOrderByCreatedAtDesc(UUID tenantId, String facilityId);
    List<ReferralEntity> findByTenantIdAndFacilityIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String facilityId, String status);

    /**
     * Pool-scoped worklist (Stage 3 routing, V020 columns): referrals routed to a specialty
     * pool, oldest first — queue semantics for the virtual-hospital provider surface.
     */
    List<ReferralEntity> findByTenantIdAndRoutingPoolIdAndStatusInOrderByCreatedAtAsc(
            UUID tenantId, String routingPoolId, Collection<String> statuses);
}
