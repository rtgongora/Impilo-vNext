package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.EligibilityCheckEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface EligibilityCheckRepository extends JpaRepository<EligibilityCheckEntity, UUID> {

    List<EligibilityCheckEntity> findByTenantIdAndPatientRef(UUID tenantId, String patientRef);
}
