package zw.gov.mohcc.impilo.madi.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import zw.gov.mohcc.impilo.madi.persistence.entity.MhpActivationEntity;

public interface MhpActivationRepository extends JpaRepository<MhpActivationEntity, UUID> {
    Optional<MhpActivationEntity> findByActivationIdAndTenantId(UUID activationId, UUID tenantId);
    List<MhpActivationEntity> findByTenantIdAndStatusOrderByActivatedAtDesc(UUID tenantId, String status);
    List<MhpActivationEntity> findByTenantIdAndTraumaEpisodeIdOrderByActivatedAtDesc(UUID tenantId, UUID traumaEpisodeId);
}
