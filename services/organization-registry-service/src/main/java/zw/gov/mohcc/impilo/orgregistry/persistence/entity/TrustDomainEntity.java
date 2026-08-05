package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A trust domain: the governed data-control and disclosure boundary (v1.3.8 §3.2).
 *
 * <p>It identifies the applicable controller or controller <em>arrangement</em>. It is not
 * itself necessarily a legal person, and it is never automatically the controller of
 * anything (§1 boundary line, §3A.2). Membership of a domain grants no clinical access,
 * and governance rights over a domain grant no clinical read.
 *
 * <p>{@code dataControllerLegalName} is nullable on purpose. Where the controlling party is
 * an open {@code [L]} determination — as it is for MoHCC hospital records, §26.2 #1 — the
 * only honest value is none, and {@code controllerDeclarationState} says so explicitly
 * rather than leaving a NULL whose meaning has to be guessed.
 */
@Entity
@Table(name = "trust_domain", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class TrustDomainEntity {

    @Id
    private UUID trustDomainId;

    @Column(nullable = false, length = 48, unique = true)
    private String code;

    @Column(nullable = false)
    private String displayName;

    /** MINISTRY | PRIVATE_GROUP | MISSION | LOCAL_AUTHORITY | SECURITY_SECTOR | UNIVERSITY | REGULATOR | PARTNER */
    @Column(nullable = false, length = 32)
    private String controllerType;

    /** NULL means UNDETERMINED. Never inferred from hosting, custody or operation (§3A.2). */
    private String dataControllerLegalName;

    @Column(columnDefinition = "jsonb")
    private String dataControllerContact;

    /** UNDETERMINED | PENDING_GOVERNANCE_REVIEW | DECLARED */
    @Column(nullable = false, length = 32)
    private String controllerDeclarationState = "UNDETERMINED";

    private UUID jurisdictionId;

    /** §3.3: PROVISIONAL -> ACCREDITED -> (SUSPENDED &lt;-&gt; ACCREDITED) -> WITHDRAWN */
    @Column(nullable = false, length = 24)
    private String accreditationStatus = "PROVISIONAL";

    private OffsetDateTime accreditedAt;
    private OffsetDateTime suspendedAt;
    private OffsetDateTime withdrawnAt;
    private UUID defaultDataSharingPolicyId;

    /** NATIONAL | INSTITUTIONAL */
    @Column(nullable = false, length = 24)
    private String keyCustodyMode = "NATIONAL";

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(nullable = false, length = 128)
    private String createdByActor;

    @Version
    @Column(nullable = false)
    private Integer version = 0;

    /** True only when a controlling party has actually been determined and recorded. */
    public boolean hasDeclaredController() {
        return "DECLARED".equals(controllerDeclarationState) && dataControllerLegalName != null;
    }
}
