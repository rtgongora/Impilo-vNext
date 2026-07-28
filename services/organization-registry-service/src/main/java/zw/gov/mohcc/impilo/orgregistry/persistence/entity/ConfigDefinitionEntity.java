package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The STABLE identity of a configurable thing — "the student index fee schedule" — independent of
 * what it currently says. Its versions carry the content. Cases pin versions, never definitions,
 * which is what lets the rules change without rewriting the cases decided under the old ones.
 */
@Entity
@Table(name = "regulatory_config_definition", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class ConfigDefinitionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID packId;

    @Column(nullable = false, length = 48)
    private String typeCode;

    @Column(nullable = false, length = 96)
    private String definitionKey;

    @Column(nullable = false, length = 160)
    private String label;

    private String description;

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
