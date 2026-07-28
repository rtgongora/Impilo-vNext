package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One definition version's membership of a release. {@code definitionId} is carried denormalised so
 * the database — not the service — refuses a release containing two versions of the same
 * definition.
 */
@Entity
@Table(name = "regulatory_config_release_item", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class ConfigReleaseItemEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID releaseId;

    @Column(nullable = false)
    private UUID definitionId;

    @Column(nullable = false)
    private UUID definitionVersionId;

    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
