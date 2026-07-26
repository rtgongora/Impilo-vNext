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

    /**
     * Facility-scoped teardown (W5, D-P7): revoke the actor's ACTIVE
     * WORK_CONTEXT tokens bound to ONE facility — an assignment ending at
     * facility A must not kill a live session at facility B.
     */
    @Modifying
    @Query(value = "UPDATE tshepo_identity.scoped_token"
            + " SET status = 'REVOKED', revoked_at = :now"
            + " WHERE actor_id = :actorId AND status = 'ACTIVE'"
            + "   AND token_kind = 'WORK_CONTEXT'"
            + "   AND context_claims->>'facility_id' = :facilityId", nativeQuery = true)
    int revokeWorkContextForActorAtFacility(@Param("actorId") String actorId,
                                            @Param("facilityId") String facilityId,
                                            @Param("now") Instant now);

    /**
     * Organisation-scoped teardown (RB-1): revoke the actor's ACTIVE WORK_CONTEXT tokens bound to
     * ONE regulatory organisation. The org-scoped sibling of
     * {@link #revokeWorkContextForActorAtFacility} — a registrar whose appointment at council A
     * ends must not lose a live session at council B, where they may hold a separate appointment.
     *
     * <p>Matches on the {@code org_id} context claim, which is what
     * {@code TokenIssuanceService} writes for the org-scoped branch (the facility claim is absent
     * on those tokens, so the facility query above can never reach them).</p>
     */
    @Modifying
    @Query(value = "UPDATE tshepo_identity.scoped_token"
            + " SET status = 'REVOKED', revoked_at = :now"
            + " WHERE actor_id = :actorId AND status = 'ACTIVE'"
            + "   AND token_kind = 'WORK_CONTEXT'"
            + "   AND context_claims->>'org_id' = :organisationId", nativeQuery = true)
    int revokeWorkContextForActorAtOrganisation(@Param("actorId") String actorId,
                                                @Param("organisationId") String organisationId,
                                                @Param("now") Instant now);
}
