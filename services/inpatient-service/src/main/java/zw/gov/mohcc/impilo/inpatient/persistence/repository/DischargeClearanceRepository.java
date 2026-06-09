package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.DischargeClearanceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DischargeClearanceRepository extends JpaRepository<DischargeClearanceEntity, UUID> {
    List<DischargeClearanceEntity> findByTenantIdAndEncounterIdOrderByClearanceTypeAsc(UUID tenantId, UUID encounterId);

    Optional<DischargeClearanceEntity> findByTenantIdAndEncounterIdAndClearanceType(
            UUID tenantId, UUID encounterId, String clearanceType);
}
