package zw.gov.mohcc.impilo.patientsafety.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.patientsafety.domain.SafetyReportEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SafetyReportRepository extends JpaRepository<SafetyReportEntity, UUID> {

    Optional<SafetyReportEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SafetyReportEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<SafetyReportEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<SafetyReportEntity> findByTenantIdAndReporterActorIdOrderByCreatedAtDesc(UUID tenantId, String reporterActorId);

    List<SafetyReportEntity> findByTenantIdAndReporterFacilityIdOrderByCreatedAtDesc(UUID tenantId, String facilityId);

    long countByTenantIdAndReferenceCodeStartingWith(UUID tenantId, String prefix);
}
