package zw.gov.mohcc.impilo.datagovernance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.datagovernance.domain.GrantEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GrantRepository extends JpaRepository<GrantEntity, UUID> {

    Optional<GrantEntity> findByTenantIdAndDatasetIdAndPrincipalIdAndPurposeOfUse(
            UUID tenantId, UUID datasetId, String principalId, String purposeOfUse);

    @Query("SELECT g FROM GrantEntity g WHERE g.tenantId = :tenantId " +
            "AND g.datasetId = :datasetId AND g.principalId = :principalId " +
            "AND g.purposeOfUse = :purposeOfUse AND g.revoked = false " +
            "AND (g.expiresAt IS NULL OR g.expiresAt > :now)")
    Optional<GrantEntity> findActiveGrant(
            @Param("tenantId") UUID tenantId,
            @Param("datasetId") UUID datasetId,
            @Param("principalId") String principalId,
            @Param("purposeOfUse") String purposeOfUse,
            @Param("now") OffsetDateTime now);

    List<GrantEntity> findByTenantIdAndPrincipalId(UUID tenantId, String principalId);
}
