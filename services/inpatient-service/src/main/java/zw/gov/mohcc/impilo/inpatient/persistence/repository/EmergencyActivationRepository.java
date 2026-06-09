package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EmergencyActivationEntity;

import java.util.List;
import java.util.UUID;

public interface EmergencyActivationRepository extends JpaRepository<EmergencyActivationEntity, UUID> {
    List<EmergencyActivationEntity> findByTenantIdOrderByActivationTimeDesc(UUID tenantId);
}
