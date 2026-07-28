package zw.gov.mohcc.impilo.mentalhealth.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.InvoluntaryEpisodeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoluntaryEpisodeRepository extends JpaRepository<InvoluntaryEpisodeEntity, UUID> {
    Optional<InvoluntaryEpisodeEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<InvoluntaryEpisodeEntity> findByTenantIdAndReferralIdAndStatus(UUID tenantId, UUID referralId, String status);
    List<InvoluntaryEpisodeEntity> findByTenantIdAndReferralIdOrderByAuthorisedAtDesc(UUID tenantId, UUID referralId);
}
