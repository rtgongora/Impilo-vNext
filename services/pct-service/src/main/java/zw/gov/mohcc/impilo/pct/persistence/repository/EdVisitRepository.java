package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.EdVisitEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EdVisitRepository extends JpaRepository<EdVisitEntity, UUID> {
    Optional<EdVisitEntity> findByJourneyId(String journeyId);
    List<EdVisitEntity> findByTenantIdAndFacilityIdAndStatusInOrderByCreatedAtDesc(
            UUID tenantId, UUID facilityId, List<String> statuses);
    List<EdVisitEntity> findByTenantIdAndPatientCpidOrderByCreatedAtDesc(UUID tenantId, String patientCpid);
}
