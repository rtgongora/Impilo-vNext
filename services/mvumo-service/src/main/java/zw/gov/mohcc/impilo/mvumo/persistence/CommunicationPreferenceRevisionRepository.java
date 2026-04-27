package zw.gov.mohcc.impilo.mvumo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunicationPreferenceRevisionRepository extends JpaRepository<CommunicationPreferenceRevisionEntity, UUID> {

    @Query(
            "SELECT COALESCE(MAX(r.bundleVersion), 0) FROM CommunicationPreferenceRevisionEntity r"
                    + " WHERE r.tenantId = :tenantId AND r.patientRef = :patientRef")
    int maxBundleVersion(@Param("tenantId") UUID tenantId, @Param("patientRef") String patientRef);

    List<CommunicationPreferenceRevisionEntity> findByTenantIdAndPatientRefOrderByBundleVersionDesc(
            UUID tenantId, String patientRef);

    Optional<CommunicationPreferenceRevisionEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
