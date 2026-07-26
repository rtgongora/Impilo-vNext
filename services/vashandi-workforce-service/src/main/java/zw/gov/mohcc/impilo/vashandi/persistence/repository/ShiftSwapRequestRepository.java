package zw.gov.mohcc.impilo.vashandi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ShiftSwapRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftSwapRequestRepository extends JpaRepository<ShiftSwapRequestEntity, UUID> {

    List<ShiftSwapRequestEntity> findByTenantIdAndFacilityIdOrderByCreatedAtDesc(
            UUID tenantId, UUID facilityId);

    List<ShiftSwapRequestEntity> findByTenantIdAndFacilityIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, UUID facilityId, String status);

    Optional<ShiftSwapRequestEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Guards the one-live-request-per-shift rule in the service so callers get a 409 with an
     * explanation rather than a raw constraint violation from the partial unique index.
     */
    Optional<ShiftSwapRequestEntity> findByRequestingShiftIdAndStatus(UUID requestingShiftId, String status);
}
