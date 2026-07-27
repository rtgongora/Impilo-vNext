package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgInvitationEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrgInvitationRepository extends JpaRepository<OrgInvitationEntity, UUID> {

    List<OrgInvitationEntity> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    /** Look up by the SHA-256 hash of the presented token (V010) — the raw token is never stored. */
    Optional<OrgInvitationEntity> findByTokenHashAndTenantId(String tokenHash, UUID tenantId);

    /**
     * Single-use redeem (V010), mirroring varapi's {@code ProviderClaimTokenRepository.redeem}.
     *
     * <p>The row-lock the conditional UPDATE takes serialises concurrent acceptances of the same
     * token, so exactly one caller's UPDATE matches a PENDING row (rowcount == 1) and every other
     * sees 0. Status-check-then-update, which this replaces, had a window between the check and the
     * write in which two acceptances could both pass — the account-takeover race the provider rail
     * closes this way.</p>
     */
    @Modifying
    @Query("update OrgInvitationEntity i set i.status = 'ACCEPTED', i.acceptedAt = :now, "
            + "i.acceptedByHealthId = :healthId "
            + "where i.id = :id and i.status = 'PENDING'")
    int redeem(@Param("id") UUID id, @Param("now") OffsetDateTime now, @Param("healthId") String healthId);
}
