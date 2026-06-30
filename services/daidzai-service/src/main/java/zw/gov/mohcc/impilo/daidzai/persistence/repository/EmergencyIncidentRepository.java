package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmergencyIncidentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmergencyIncidentRepository extends JpaRepository<EmergencyIncidentEntity, UUID> {
    Optional<EmergencyIncidentEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<EmergencyIncidentEntity> findByTenantIdOrderByOpenedAtDesc(UUID tenantId);
    List<EmergencyIncidentEntity> findByTenantIdAndIncidentTypeOrderByOpenedAtDesc(UUID tenantId, String incidentType);
    List<EmergencyIncidentEntity> findByTenantIdAndStatusOrderByOpenedAtDesc(UUID tenantId, String status);
}
