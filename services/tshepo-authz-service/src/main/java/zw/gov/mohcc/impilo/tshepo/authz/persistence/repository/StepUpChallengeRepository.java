package zw.gov.mohcc.impilo.tshepo.authz.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.StepUpChallengeEntity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StepUpChallengeRepository extends JpaRepository<StepUpChallengeEntity, UUID> {

    /**
     * Find a recently completed step-up challenge for the given actor within
     * the step-up window (i.e., step-up is still valid).
     */
    @Query("SELECT s FROM StepUpChallengeEntity s WHERE s.tenantId = :tenantId " +
           "AND s.actorId = :actorId AND s.status = 'COMPLETED' " +
           "AND s.completedAt > :windowStart ORDER BY s.completedAt DESC")
    Optional<StepUpChallengeEntity> findRecentlyCompletedByActorId(
            @Param("tenantId") UUID tenantId,
            @Param("actorId") String actorId,
            @Param("windowStart") Instant windowStart);

    /**
     * Find a pending (non-expired) challenge by ID and tenant.
     */
    @Query("SELECT s FROM StepUpChallengeEntity s WHERE s.id = :id " +
           "AND s.tenantId = :tenantId AND s.status = 'PENDING' AND s.expiresAt > :now")
    Optional<StepUpChallengeEntity> findPendingById(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("now") Instant now);
}
