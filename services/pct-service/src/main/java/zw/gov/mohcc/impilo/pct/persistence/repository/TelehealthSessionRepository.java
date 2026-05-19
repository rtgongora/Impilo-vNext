package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.TelehealthSessionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TelehealthSessionRepository extends JpaRepository<TelehealthSessionEntity, UUID> {
    Optional<TelehealthSessionEntity> findByTenantIdAndSessionId(UUID tenantId, UUID sessionId);
    List<TelehealthSessionEntity> findByTenantIdAndPatientCpidOrderByCreatedAtDesc(UUID tenantId, String patientCpid);
    List<TelehealthSessionEntity> findByTenantIdAndPatientCpidAndStatusOrderByCreatedAtDesc(UUID tenantId, String patientCpid, String status);
    List<TelehealthSessionEntity> findByTenantIdAndFacilityIdOrderByCreatedAtDesc(UUID tenantId, String facilityId);
    List<TelehealthSessionEntity> findByTenantIdAndFacilityIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String facilityId, String status);
}
