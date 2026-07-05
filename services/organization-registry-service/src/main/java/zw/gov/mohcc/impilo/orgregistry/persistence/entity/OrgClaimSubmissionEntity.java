package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Channel-C delegated onboarding claim: an authorized representative asserts
 * that a person holds a role within the organization, pending adjudication.
 */
@Entity
@Table(name = "org_registry_claim_submission", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class OrgClaimSubmissionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private UUID submittedByRepId;

    @Column(nullable = false, length = 128)
    private String subjectHealthId;

    @Column(nullable = false, length = 128)
    private String claimedRole;

    @Column(nullable = false, length = 64)
    private String trustBasis = "ORG_DELEGATED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String evidence;

    /** SUBMITTED | UNDER_REVIEW | ACCEPTED | REJECTED */
    @Column(nullable = false, length = 32)
    private String status = "SUBMITTED";

    @Column(length = 255)
    private String adjudicationRef;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
