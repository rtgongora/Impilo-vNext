package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A regulator's configuration pack: the container its definitions and releases belong to. One
 * pack per regulator in practice, but the key is explicit so a regulator can hold more than one
 * (for example a separate pack for institutions) without the two colliding.
 */
@Entity
@Table(name = "regulatory_config_pack", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class ConfigPackEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 64)
    private String packKey;

    @Column(nullable = false, length = 160)
    private String label;

    private String description;

    @Column(nullable = false, length = 24)
    private String status = "ACTIVE";

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
