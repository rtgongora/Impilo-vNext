package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.SafetyIncidentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SafetyIncidentRepository extends JpaRepository<SafetyIncidentEntity, UUID> {

    Optional<SafetyIncidentEntity> findByCaseId(UUID caseId);

    List<SafetyIncidentEntity> findByTenantIdAndIncidentType(UUID tenantId, String incidentType);

    List<SafetyIncidentEntity> findByTenantIdAndSentinelTrue(UUID tenantId);
}
