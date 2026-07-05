package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "org_registry_affiliation", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class AffiliationEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID organizationId;

    /** FACILITY | PROVIDER */
    @Column(nullable = false, length = 32)
    private String subjectType;

    @Column(nullable = false, length = 255)
    private String subjectRef;

    @Column(nullable = false, length = 64)
    private String affiliationType;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(length = 64)
    private String sourceChannel;

    private LocalDate validFrom;
    private LocalDate validTo;

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
