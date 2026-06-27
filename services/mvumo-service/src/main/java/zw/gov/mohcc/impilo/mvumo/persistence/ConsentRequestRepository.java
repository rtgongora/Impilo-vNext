package zw.gov.mohcc.impilo.mvumo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentRequestRepository extends JpaRepository<ConsentRequestEntity, UUID> {

    Optional<ConsentRequestEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ConsentRequestEntity> findByTenantIdAndSubjectPatientRefOrderByCreatedAtDesc(UUID tenantId, String patientRef);

    List<ConsentRequestEntity> findByTenantIdAndEncounterRefOrderByCreatedAtDesc(UUID tenantId, String encounterRef);

    /** Granted requests that carry a trust-layer directive — candidates for drift reconciliation. */
    List<ConsentRequestEntity> findByStateInAndTshepoConsentIdIsNotNull(List<String> states);
}
