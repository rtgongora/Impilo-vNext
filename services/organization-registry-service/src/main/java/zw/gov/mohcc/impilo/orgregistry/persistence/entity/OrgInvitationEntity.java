package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Organisation-invitation onboarding: an organisation's authorized representative
 * invites a person to hold a role (e.g. facility administrator). The invitee
 * accepts to establish an affiliation — the inviting organisation is the authority,
 * so no adjudication queue is involved.
 *
 * <p>State machine: {@code PENDING → ACCEPTED | REVOKED | EXPIRED}. ACCEPTED,
 * REVOKED and EXPIRED are terminal.
 */
@Entity
@Table(name = "org_registry_invitation", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class OrgInvitationEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID organizationId;

    private UUID facilityUuid;

    /** Set when the inviter is an authorized representative (delegated-onboarding path, V005). */
    private UUID invitedByRepId;

    /**
     * Set when the inviter is a regulatory appointment-holder administrator (RB-6, V011). One of
     * {@link #invitedByRepId} / {@code invitedByHealthId} is always present (DB CHECK).
     */
    @Column(name = "invited_by_health_id", length = 64)
    private String invitedByHealthId;

    @Column(nullable = false, length = 255)
    private String inviteeIdentifier;

    /** EMAIL | HEALTH_ID */
    @Column(nullable = false, length = 32)
    private String inviteeIdentifierType = "EMAIL";

    @Column(nullable = false, length = 128)
    private String role;

    /**
     * Governed appointment role (V010). When set to a regulatory role, acceptance mints a
     * PENDING_VERIFICATION regulatory appointment (source=INVITATION) rather than an affiliation.
     * Null keeps the legacy free-text {@link #role} affiliation path.
     */
    @Column(name = "role_code", length = 48)
    private String roleCode;

    /** SHA-256 hex of the single-use token — only the hash is ever persisted (V010). */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /**
     * The raw token, held only in memory on the object returned by {@code create} so the issuer can
     * build the one-time link. Never persisted — there is no column for it.
     */
    @jakarta.persistence.Transient
    private String rawToken;

    /** PENDING | ACCEPTED | REVOKED | EXPIRED */
    @Column(nullable = false, length = 32)
    private String status = "PENDING";

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    private OffsetDateTime acceptedAt;

    @Column(length = 128)
    private String acceptedByHealthId;

    private UUID affiliationId;
}
