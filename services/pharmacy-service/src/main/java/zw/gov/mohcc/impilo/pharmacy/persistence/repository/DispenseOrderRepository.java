package zw.gov.mohcc.impilo.pharmacy.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.OrderPriority;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DispenseOrderRepository extends JpaRepository<DispenseOrderEntity, UUID> {

    Optional<DispenseOrderEntity> findByOrosOrderId(String orosOrderId);

    Page<DispenseOrderEntity> findByFacilityIdAndStatus(UUID facilityId, DispenseStatus status, Pageable pageable);

    Page<DispenseOrderEntity> findByFacilityId(UUID facilityId, Pageable pageable);

    Page<DispenseOrderEntity> findByFacilityIdAndWorkspaceIdAndStatus(
            UUID facilityId, UUID workspaceId, DispenseStatus status, Pageable pageable);

    List<DispenseOrderEntity> findByPatientCpidOrderByCreatedAtDesc(String patientCpid);

    Page<DispenseOrderEntity> findByFacilityIdAndPriority(UUID facilityId, OrderPriority priority, Pageable pageable);

    Page<DispenseOrderEntity> findByFacilityIdAndStatusAndPriority(
            UUID facilityId, DispenseStatus status, OrderPriority priority, Pageable pageable);

    Page<DispenseOrderEntity> findByFacilityIdAndWorkspaceId(UUID facilityId, UUID workspaceId, Pageable pageable);

    Page<DispenseOrderEntity> findByFacilityIdAndWorkspaceIdAndStatusAndPriority(
            UUID facilityId, UUID workspaceId, DispenseStatus status, OrderPriority priority, Pageable pageable);

    // ── OF-B12 §13.3.1 anomaly sweep queries ───────────────────────────

    /**
     * Unbound-dispense anomaly candidates: episodes that completed for an
     * OROS medication-profile order WITHOUT a prescription claim, not yet flagged.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT o FROM DispenseOrderEntity o
            WHERE o.status = zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus.DISPENSED
              AND o.orosOrderId IS NOT NULL
              AND o.prescriptionClaimId IS NULL
              AND o.claimAnomalyFlaggedAt IS NULL
              AND o.completedAt < :cutoff
            """)
    List<DispenseOrderEntity> findUnboundDispenseAnomalies(
            @org.springframework.data.repository.query.Param("cutoff") java.time.OffsetDateTime cutoff);

    /**
     * Claim-without-episode-completion anomaly candidates: episodes carrying a
     * claim that never reached dispense within the claim TTL, not yet flagged.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT o FROM DispenseOrderEntity o
            WHERE o.prescriptionClaimId IS NOT NULL
              AND o.claimAnomalyFlaggedAt IS NULL
              AND o.status IN (zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus.PENDING,
                               zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus.ACCEPTED,
                               zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus.PICKING,
                               zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus.READY)
              AND o.createdAt < :ttlCutoff
            """)
    List<DispenseOrderEntity> findStalledClaimEpisodes(
            @org.springframework.data.repository.query.Param("ttlCutoff") java.time.OffsetDateTime ttlCutoff);
}
