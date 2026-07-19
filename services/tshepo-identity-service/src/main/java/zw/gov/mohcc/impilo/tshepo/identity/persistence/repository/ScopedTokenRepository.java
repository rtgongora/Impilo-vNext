package zw.gov.mohcc.impilo.tshepo.identity.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.ScopedTokenEntity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScopedTokenRepository extends JpaRepository<ScopedTokenEntity, UUID> {

    Optional<ScopedTokenEntity> findByJti(String jti);

    Optional<ScopedTokenEntity> findByTenantIdAndJti(UUID tenantId, String jti);

    /**
     * Revocation teardown (D-P3): revoke every ACTIVE token held by an actor —
     * used when a provider's privileges are revoked (licence lapse, suspension)
     * so live work sessions do not outlive the revocation.
     */
    @Modifying
    @Query("UPDATE ScopedTokenEntity t"
            + " SET t.status = 'REVOKED', t.revokedAt = :now"
            + " WHERE t.actorId = :actorId AND t.status = 'ACTIVE'")
    int revokeAllForActor(@Param("actorId") String actorId, @Param("now") Instant now);
}
