package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralEntity;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<ReferralEntity, UUID> {
    Optional<ReferralEntity> findByTenantIdAndReferralId(UUID tenantId, UUID referralId);

    // TM-B11: offline replay dedupe — one referral per (tenant, client package id).
    Optional<ReferralEntity> findByTenantIdAndClientOfflineId(UUID tenantId, String clientOfflineId);

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

    /**
     * Lifecycle-timer expiry sweep (TM-B2): oldest-first batch of referrals still
     * awaiting acceptance (pre-accept statuses SUBMITTED/ROUTED). The awaiting-since
     * threshold is applied in code per status (submittedAt / routedAt / createdAt),
     * so this returns the candidate window and the job decides staleness.
     */
    List<ReferralEntity> findTop100ByStatusInOrderByCreatedAtAsc(Collection<String> statuses);

    /**
     * Lifecycle-timer no-show sweep (TM-B4): SCHEDULED teleconsults whose scheduled
     * start is already past the no-show cutoff, oldest first — candidates for ABANDONED.
     */
    List<ReferralEntity> findTop100ByStatusAndScheduledAtBeforeOrderByScheduledAtAsc(
            String status, OffsetDateTime scheduledAtBefore);
}
