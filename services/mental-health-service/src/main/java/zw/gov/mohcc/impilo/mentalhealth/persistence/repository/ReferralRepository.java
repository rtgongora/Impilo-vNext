package zw.gov.mohcc.impilo.mentalhealth.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.ReferralEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<ReferralEntity, UUID> {
    Optional<ReferralEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<ReferralEntity> findByTenantIdAndHandoverId(UUID tenantId, UUID handoverId);
    List<ReferralEntity> findByTenantIdAndStatusOrderByRequestedAtDesc(UUID tenantId, String status);
    List<ReferralEntity> findByTenantIdAndEpisodeIdOrderByRequestedAtDesc(UUID tenantId, UUID episodeId);
}
