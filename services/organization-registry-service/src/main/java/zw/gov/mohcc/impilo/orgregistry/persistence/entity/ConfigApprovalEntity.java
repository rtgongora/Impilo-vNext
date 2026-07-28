package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One approver's decision on a configuration release. Four-eyes is enforced in schema: a trigger
 * refuses an approval by the person who submitted the release, and a unique index refuses a second
 * decision from the same approver. Both are the invariants workforce-governance's
 * TwoPersonApprovalService enforces in Java for access requests — reproduced here at the database
 * level rather than reached across a service boundary that is bound to a different aggregate.
 */
@Entity
@Table(name = "regulatory_config_approval", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class ConfigApprovalEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID releaseId;

    @Column(nullable = false, length = 128)
    private String approverHealthId;

    @Column(length = 48)
    private String approverRole;

    @Column(nullable = false, length = 16)
    private String decision;

    private String note;

    @Column(nullable = false)
    private OffsetDateTime decidedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (decidedAt == null) {
            decidedAt = OffsetDateTime.now();
        }
    }
}
