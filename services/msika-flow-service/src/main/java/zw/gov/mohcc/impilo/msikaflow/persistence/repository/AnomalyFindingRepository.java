package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.domain.AnomalyFindingStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.AnomalyFindingType;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.AnomalyFindingEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnomalyFindingRepository extends JpaRepository<AnomalyFindingEntity, String> {

    /** Sweep-idempotency probe — one finding per (tenant, dedupe key), ever. */
    Optional<AnomalyFindingEntity> findFirstByTenantIdAndDedupeKey(UUID tenantId, String dedupeKey);

    Optional<AnomalyFindingEntity> findByFindingIdAndTenantId(String findingId, UUID tenantId);

    Page<AnomalyFindingEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<AnomalyFindingEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, AnomalyFindingStatus status, Pageable pageable);

    Page<AnomalyFindingEntity> findByTenantIdAndFindingTypeOrderByCreatedAtDesc(
            UUID tenantId, AnomalyFindingType findingType, Pageable pageable);

    Page<AnomalyFindingEntity> findByTenantIdAndStatusAndFindingTypeOrderByCreatedAtDesc(
            UUID tenantId, AnomalyFindingStatus status, AnomalyFindingType findingType, Pageable pageable);

    long countByTenantIdAndStatus(UUID tenantId, AnomalyFindingStatus status);
}
