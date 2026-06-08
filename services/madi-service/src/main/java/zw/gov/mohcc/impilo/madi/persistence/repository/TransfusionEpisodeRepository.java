package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.TransfusionEpisodeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransfusionEpisodeRepository extends JpaRepository<TransfusionEpisodeEntity, Long> {
    Optional<TransfusionEpisodeEntity> findByEpisodeIdAndTenantId(UUID episodeId, UUID tenantId);
    List<TransfusionEpisodeEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
