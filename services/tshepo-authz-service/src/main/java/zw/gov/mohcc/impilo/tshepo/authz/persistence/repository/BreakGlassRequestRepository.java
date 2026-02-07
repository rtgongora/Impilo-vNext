package zw.gov.mohcc.impilo.tshepo.authz.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.BreakGlassRequestEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BreakGlassRequestRepository extends JpaRepository<BreakGlassRequestEntity, UUID> {

    /**
     * Find an active (non-expired) break-glass request for the given actor in a tenant.
     */
    @Query("SELECT b FROM BreakGlassRequestEntity b WHERE b.tenantId = :tenantId " +
           "AND b.actorId = :actorId AND b.expiresAt > :now ORDER BY b.grantedAt DESC")
    List<BreakGlassRequestEntity> findActiveByTenantIdAndActorId(
            @Param("tenantId") UUID tenantId,
            @Param("actorId") String actorId,
            @Param("now") Instant now);

    /**
     * Find all pending-review break-glass requests for a tenant.
     */
    List<BreakGlassRequestEntity> findByTenantIdAndReviewStatusOrderByGrantedAtDesc(
            UUID tenantId, String reviewStatus);

    /**
     * Find a break-glass request by ID and tenant (tenant isolation).
     */
    Optional<BreakGlassRequestEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
