package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.costa.domain.entity.WaiverEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.WaiverStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaiverRepository extends JpaRepository<WaiverEntity, UUID> {

    List<WaiverEntity> findTop100ByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<WaiverEntity> findTop100ByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, WaiverStatus status);

    List<WaiverEntity> findTop100ByTenantIdAndPatientCpidOrderByCreatedAtDesc(UUID tenantId, String patientCpid);

    Optional<WaiverEntity> findByWaiverIdAndTenantId(UUID waiverId, UUID tenantId);

    boolean existsByTenantIdAndWaiverReference(UUID tenantId, String waiverReference);
}
