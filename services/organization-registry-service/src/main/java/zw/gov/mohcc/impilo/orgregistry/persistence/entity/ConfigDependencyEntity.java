package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One reference a definition version makes to another definition — a workflow stage naming a form,
 * a fee naming a service. Release activation resolves every non-optional dependency against the
 * release's own contents and refuses to activate when one is unresolved, so a council cannot go
 * live with a workflow that points at a form it never included.
 */
@Entity
@Table(name = "regulatory_config_dependency", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class ConfigDependencyEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID definitionVersionId;

    @Column(nullable = false, length = 48)
    private String requiredTypeCode;

    @Column(nullable = false, length = 96)
    private String requiredDefinitionKey;

    @Column(nullable = false)
    private boolean isOptional;

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
