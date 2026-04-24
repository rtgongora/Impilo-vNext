package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vito.persistence.entity.PatientShareRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientShareRequestRepository extends JpaRepository<PatientShareRequestEntity, Long> {

    List<PatientShareRequestEntity> findByTenantIdAndHealthIdOrderByCreatedAtDesc(UUID tenantId, UUID healthId);

    Optional<PatientShareRequestEntity> findByIdAndTenantIdAndHealthId(long id, UUID tenantId, UUID healthId);
}
