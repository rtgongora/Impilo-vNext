package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.TraumaEpisodeEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TraumaEpisodeRepository extends JpaRepository<TraumaEpisodeEntity, UUID> {
    Optional<TraumaEpisodeEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    /** Idempotency anchor for the dual-entry mint: one episode per (tenant, origin_key). */
    Optional<TraumaEpisodeEntity> findByTenantIdAndOriginKey(UUID tenantId, String originKey);
    Optional<TraumaEpisodeEntity> findByTenantIdAndIncidentId(UUID tenantId, UUID incidentId);
}
