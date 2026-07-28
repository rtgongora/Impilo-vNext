package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One immutable statement of what a definition says, at a version.
 *
 * <p>Content is editable only while DRAFT. Once a version leaves DRAFT a database trigger refuses
 * any change to its payload, hash, semantic version, schema version or owning definition, and
 * refuses to walk it back into DRAFT — because a case may already be pinned to it, and editing it
 * would rewrite the rules that case was decided under. A change to an approved definition is a new
 * version, never an edit.</p>
 *
 * <p>{@code contentHash} carries the checksum-immutability discipline proven in tuso's
 * InspectionContentSeeder: the same (definition, semanticVersion) must always carry the same
 * content, so a source pack that changes content under a fixed version fails fast at import rather
 * than silently mutating live rules.</p>
 *
 * <p>{@code policyStatus} is how the platform says "this seam is built and the value is not set"
 * honestly. A fee schedule whose amount the Council has not yet resolved carries
 * PENDING_REGULATOR_APPROVAL and a decision reference — it is never defaulted to a guess.</p>
 */
@Entity
@Table(name = "regulatory_config_definition_version", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class ConfigDefinitionVersionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID definitionId;

    @Column(nullable = false, length = 32)
    private String semanticVersion;

    @Column(nullable = false)
    private int schemaVersion = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode payload;

    @Column(nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false, length = 24)
    private String lifecycleState = "DRAFT";

    /** Where an applicant reaches this capability. Required for applicant-facing types. */
    @Column(length = 256)
    private String selfServiceRoute;

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    @Column(length = 40)
    private String policyStatus;

    @Column(length = 64)
    private String policyDecisionRef;

    @Column(nullable = false, length = 128)
    private String authoredBy;

    private OffsetDateTime approvedAt;

    private UUID supersedesVersionId;

    @Column(length = 256)
    private String sourcePackRef;

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
