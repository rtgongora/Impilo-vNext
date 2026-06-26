package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.CommunityScreeningEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityScreeningRepository extends JpaRepository<CommunityScreeningEntity, UUID> {

    List<CommunityScreeningEntity> findByTenantIdAndPatientIdOrderByCreatedAtDesc(UUID tenantId, String patientId);

    Optional<CommunityScreeningEntity> findByTenantIdAndOfflineId(UUID tenantId, String offlineId);
}
