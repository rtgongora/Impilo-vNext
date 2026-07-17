package zw.gov.mohcc.impilo.participation.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.participation.persistence.entity.ContributionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContributionRepository extends JpaRepository<ContributionEntity, UUID> {

    Optional<ContributionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ContributionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<ContributionEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<ContributionEntity> findByTenantIdAndTypeOrderByCreatedAtDesc(UUID tenantId, String type);

    List<ContributionEntity> findByTenantIdAndModerationStateOrderBySupportCountDescCreatedAtDesc(
            UUID tenantId, String moderationState);

    Optional<ContributionEntity> findByTenantIdAndClaimCodeHash(UUID tenantId, String claimCodeHash);

    boolean existsByTenantIdAndReference(UUID tenantId, String reference);

    long countByTenantIdAndModerationState(UUID tenantId, String moderationState);
}
