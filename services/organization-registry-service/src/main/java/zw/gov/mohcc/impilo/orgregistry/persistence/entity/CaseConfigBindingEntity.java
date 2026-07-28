package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The immutable reference a regulatory case owns to the configuration that governed it.
 *
 * <p>This is the answer to "what were the rules when this application was submitted?". It is
 * written once, when the case is created, and a trigger refuses every update and delete — so
 * activating a new release cannot retroactively change the rules a live or completed case was
 * decided under. That property is the whole point of the Registry; without it, versioning is
 * decoration.</p>
 *
 * <p>The case itself lives in varapi or tuso. Binding by ({@code caseSystem}, {@code caseType},
 * {@code caseRef}) rather than by a foreign key keeps org-registry from growing a dependency on
 * either service's schema, and keeps regulatory workflow state out of org-registry (ROM ruling
 * R1).</p>
 */
@Entity
@Table(name = "regulatory_case_config_binding", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class CaseConfigBindingEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 32)
    private String caseSystem;

    @Column(nullable = false, length = 64)
    private String caseType;

    @Column(nullable = false, length = 128)
    private String caseRef;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private UUID packId;

    @Column(nullable = false)
    private UUID releaseId;

    @Column(nullable = false)
    private OffsetDateTime boundAt;

    @Column(length = 128)
    private String boundBy;

    @Column(length = 128)
    private String correlationId;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (boundAt == null) {
            boundAt = OffsetDateTime.now();
        }
    }
}
