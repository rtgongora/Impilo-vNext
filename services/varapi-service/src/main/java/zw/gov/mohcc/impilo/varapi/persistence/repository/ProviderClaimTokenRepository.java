package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderClaimTokenEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderClaimTokenRepository extends JpaRepository<ProviderClaimTokenEntity, Long> {

    Optional<ProviderClaimTokenEntity> findByTenantIdAndTokenHash(UUID tenantId, String tokenHash);

    List<ProviderClaimTokenEntity> findByTenantIdAndProviderId(UUID tenantId, Long providerId);

    /**
     * Atomically burn a claim token: transition ISSUED → CLAIMED in a single
     * conditional UPDATE. The {@code status='ISSUED'} predicate plus the row lock
     * the UPDATE takes serialises concurrent redemptions of the same token, so
     * only one caller's UPDATE matches a row (rowcount == 1); the loser sees 0.
     * This is the single-use guard against the account-takeover race.
     */
    @Modifying
    @Query("update ProviderClaimTokenEntity t set t.status='CLAIMED', t.claimedAt=:now, "
            + "t.claimedHealthId=:healthId where t.id=:id and t.status='ISSUED'")
    int redeem(@Param("id") Long id, @Param("now") Instant now, @Param("healthId") UUID healthId);
}
