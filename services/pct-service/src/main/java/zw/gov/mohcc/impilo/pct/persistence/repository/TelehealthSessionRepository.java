package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.TelehealthSessionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelehealthSessionRepository extends JpaRepository<TelehealthSessionEntity, UUID> {
    Optional<TelehealthSessionEntity> findByTenantIdAndSessionId(UUID tenantId, UUID sessionId);

    List<TelehealthSessionEntity> findByTenantIdAndPatientCpidOrderByCreatedAtDesc(UUID tenantId, String patientCpid);

    List<TelehealthSessionEntity> findByTenantIdAndFacilityIdOrderByCreatedAtDesc(UUID tenantId, UUID facilityId);
}
