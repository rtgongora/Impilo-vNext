package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A parent/child link between trust domains (v1.3.8 §6A).
 *
 * <p>§6A is an open {@code [L]} determination with an {@code [O]} recommendation. This table
 * lets all three options be expressed as data: a single MoHCC domain (no rows), separate
 * hospital domains (no rows), or a hierarchical family (parent rows). Choosing between them
 * later is a governance act, not a schema migration.
 *
 * <p>{@code grantsClinicalAccess} exists to be permanently false, and a CHECK constraint keeps
 * it that way. Two hospitals in one family must not reach each other's records merely because
 * they share a parent — that is what a federation agreement is for.
 */
@Entity
@Table(name = "trust_domain_relationship", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class TrustDomainRelationshipEntity {

    @Id
    private UUID relationshipId;

    @Column(nullable = false)
    private UUID parentTrustDomainId;

    @Column(nullable = false)
    private UUID childTrustDomainId;

    /** FAMILY_SUBDOMAIN | DELEGATED_GOVERNANCE */
    @Column(nullable = false, length = 32)
    private String relationshipType = "FAMILY_SUBDOMAIN";

    /** Always false. Family membership is never a clinical access route (§6A.3). */
    @Column(nullable = false)
    private Boolean grantsClinicalAccess = Boolean.FALSE;

    @Column(columnDefinition = "jsonb")
    private String delegatedGovernance;

    @Column(nullable = false)
    private OffsetDateTime effectiveFrom = OffsetDateTime.now();
    private OffsetDateTime effectiveTo;

    /** ACTIVE | SUPERSEDED | WITHDRAWN */
    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, length = 128)
    private String createdByActor;
}
