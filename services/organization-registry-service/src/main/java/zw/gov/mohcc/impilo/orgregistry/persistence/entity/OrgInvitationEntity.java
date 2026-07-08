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

    @Column(nullable = false)
    private UUID invitedByRepId;

    @Column(nullable = false, length = 255)
    private String inviteeIdentifier;

    /** EMAIL | HEALTH_ID */
    @Column(nullable = false, length = 32)
    private String inviteeIdentifierType = "EMAIL";

    @Column(nullable = false, length = 128)
    private String role;

    @Column(nullable = false, length = 128)
    private String token;

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
