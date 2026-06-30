package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmergencyRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmergencyRequestRepository extends JpaRepository<EmergencyRequestEntity, UUID> {
    Optional<EmergencyRequestEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<EmergencyRequestEntity> findByTenantIdAndIncidentId(UUID tenantId, UUID incidentId);
    List<EmergencyRequestEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
