package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vito.persistence.entity.PatientShareAccessGrantEntity;

import java.util.Optional;
import java.util.UUID;

public interface PatientShareAccessGrantRepository extends JpaRepository<PatientShareAccessGrantEntity, Long> {

    Optional<PatientShareAccessGrantEntity> findFirstByShareRequestIdOrderByIdAsc(long shareRequestId);

    Optional<PatientShareAccessGrantEntity> findByIdAndTenantId(long id, UUID tenantId);
}
