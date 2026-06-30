package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.DischargeSummaryEntity;

import java.util.Optional;
import java.util.UUID;

public interface DischargeSummaryRepository extends JpaRepository<DischargeSummaryEntity, UUID> {
    Optional<DischargeSummaryEntity> findByTenantIdAndEncounterId(UUID tenantId, UUID encounterId);
}
