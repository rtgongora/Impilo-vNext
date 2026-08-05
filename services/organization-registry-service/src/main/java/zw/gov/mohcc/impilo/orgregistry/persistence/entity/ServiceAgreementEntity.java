package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A service agreement (v1.3.8 §3A.1): the instrument binding an organisation in a trust
 * domain to a consumption profile and the responsibility profile that governs it.
 *
 * <p>At most one agreement may be ACTIVE for a given (trust domain, organisation) at any
 * instant — enforced by an exclusion constraint, because two contradictory live agreements
 * for one governed scope is a governance defect and not something to reconcile afterwards.
 */
@Entity
@Table(name = "service_agreement", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class ServiceAgreementEntity {

    @Id
    private UUID serviceAgreementId;

    @Column(nullable = false)
    private UUID trustDomainId;

    @Column(nullable = false)
    private UUID organisationId;

    @Column(nullable = false, length = 32)
    private String consumptionProfile;

    @Column(length = 8)
    private String isolationTier;

    @Column(length = 32)
    private String commercialServiceTier;

    @Column(nullable = false)
    private UUID responsibilityProfileId;

    @Column(nullable = false)
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;

    /** DRAFT | VALIDATED | PENDING_SIGNATURE | ACTIVE | SUSPENDED | EXPIRED | TERMINATED | SUPERSEDED */
    @Column(nullable = false, length = 24)
    private String status = "DRAFT";

    @Column(columnDefinition = "jsonb")
    private String signedByPlatform;

    @Column(columnDefinition = "jsonb")
    private String signedByOrganisation;

    @Column(nullable = false)
    private Integer version = 1;

    private UUID supersedesAgreementId;
    private String supersessionReason;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(nullable = false, length = 128)
    private String createdByActor;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Integer lockVersion = 0;

    public boolean isMutable() {
        return "DRAFT".equals(status) || "VALIDATED".equals(status);
    }
}
