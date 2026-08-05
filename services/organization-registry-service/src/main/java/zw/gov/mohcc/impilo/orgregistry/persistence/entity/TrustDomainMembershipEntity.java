package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Membership of an organisation or facility in a trust domain (ADR-0055 decision 2).
 *
 * <p>v1.3.8 assumed every organisation already had a known trust domain and would be
 * backfilled to MoHCC. The registry contains private-provider groups and local
 * authorities, so that would have recorded a false membership and then enforced it.
 * ADR-0055 replaced the assumption with this relationship: membership becomes ACTIVE on
 * evidence, cited in {@code sourceAuthority} and {@code provenance}, and never on the
 * subject's type, name, host, owner or regulator status.
 *
 * <p>{@code UNMAPPED} is a real state, not a blank. An operation needing an active trust
 * domain refuses while membership is unmapped, pending, suspended or ended — and says
 * which, and what governance action would clear it.
 */
@Entity
@Table(name = "trust_domain_membership", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class TrustDomainMembershipEntity {

    @Id
    private UUID membershipId;

    @Column(nullable = false)
    private UUID trustDomainId;

    /** ORGANISATION | FACILITY */
    @Column(nullable = false, length = 16)
    private String subjectType;

    @Column(nullable = false)
    private UUID subjectId;

    /** UNMAPPED | PENDING_REVIEW | ACTIVE | SUSPENDED | ENDED */
    @Column(nullable = false, length = 16)
    private String status = "UNMAPPED";

    /** Null until ACTIVE: an undetermined membership has no period to record. */
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;

    /** MOHCC_REGISTRY | GOVERNED_HIERARCHY | ACCREDITATION_DECISION | ONBOARDING_DECISION */
    @Column(nullable = false, length = 64)
    private String sourceAuthority;

    /** The evidence relied on. Required even for a draft, so the basis is never lost. */
    @Column(nullable = false, columnDefinition = "jsonb")
    private String provenance;

    @Column(nullable = false, length = 128)
    private String createdBy;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(length = 128)
    private String reviewedBy;
    private OffsetDateTime reviewedAt;

    private UUID supersedesMembershipId;

    /** True only when the membership is live now — the projection's only valid source. */
    public boolean isActiveAt(OffsetDateTime instant) {
        if (!"ACTIVE".equals(status) || effectiveFrom == null) {
            return false;
        }
        return !effectiveFrom.isAfter(instant)
                && (effectiveTo == null || effectiveTo.isAfter(instant));
    }
}
