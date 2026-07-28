package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The set of definition versions that go live together, and the unit a case binds to.
 *
 * <p>Lifecycle: DRAFT -&gt; IN_REVIEW -&gt; APPROVED -&gt; SCHEDULED -&gt; ACTIVE -&gt; RETIRED,
 * plus REJECTED, WITHDRAWN and SUPERSEDED. Contents may change only while DRAFT or IN_REVIEW; once
 * the release has been approved a trigger freezes its item set, because cases reference the release
 * and a reference is worth nothing if what it points at can still change.</p>
 */
@Entity
@Table(name = "regulatory_config_release", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class ConfigReleaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID packId;

    @Column(nullable = false, length = 64)
    private String releaseKey;

    @Column(nullable = false, length = 160)
    private String label;

    private String notes;

    @Column(nullable = false, length = 24)
    private String lifecycleState = "DRAFT";

    @Column(length = 128)
    private String submittedBy;

    private OffsetDateTime submittedAt;
    private OffsetDateTime scheduledFor;
    private OffsetDateTime activatedAt;

    @Column(length = 128)
    private String activatedBy;

    private OffsetDateTime retiredAt;

    @Column(length = 128)
    private String retiredBy;

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
