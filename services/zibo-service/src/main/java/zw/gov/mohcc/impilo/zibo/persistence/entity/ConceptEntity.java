package zw.gov.mohcc.impilo.zibo.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.zibo.domain.AuthorityScope;

/**
 * One concept, projected out of an artifact's {@code content_json} so it can be indexed.
 *
 * <p>Concepts previously existed only inside the JSONB blob, and every read parsed the whole
 * document and scanned {@code concept[]} linearly — on every validation. That is wasteful at 254
 * concepts and impossible at LOINC's ~109,000, and it left no way to search at all.</p>
 *
 * <p><b>This is a projection, never the origin.</b> {@code content_json} remains the artifact of
 * record; these rows are rebuilt from it on publish and can be dropped and reconstructed without
 * losing anything.</p>
 */
@Entity
@Table(name = "zibo_concept")
public class ConceptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "concept_id", nullable = false)
    private UUID conceptId;

    /** NATIONAL rows carry a null {@link #tenantId} and are readable from every plane. */
    @Enumerated(EnumType.STRING)
    @Column(name = "authority_scope", nullable = false, length = 20)
    private AuthorityScope authorityScope;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(name = "system", nullable = false, length = 500)
    private String system;

    @Column(name = "version", nullable = false, length = 50)
    private String version;

    @Column(name = "code", nullable = false, length = 255)
    private String code;

    @Column(name = "display", columnDefinition = "TEXT")
    private String display;

    @Column(name = "definition", columnDefinition = "TEXT")
    private String definition;

    /**
     * FHIR's {@code CodeSystem.concept} has no {@code active} element — inactivity is expressed as
     * a property. Projected to a column because {@code $expand(activeOnly)} needs it in the WHERE
     * clause rather than in a JSONB scan.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "designations", columnDefinition = "jsonb")
    private String designations = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties = "{}";

    @Column(name = "parent_code", length = 255)
    private String parentCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getConceptId() { return conceptId; }
    public void setConceptId(UUID conceptId) { this.conceptId = conceptId; }

    public AuthorityScope getAuthorityScope() { return authorityScope; }
    public void setAuthorityScope(AuthorityScope authorityScope) { this.authorityScope = authorityScope; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getArtifactId() { return artifactId; }
    public void setArtifactId(UUID artifactId) { this.artifactId = artifactId; }

    public String getSystem() { return system; }
    public void setSystem(String system) { this.system = system; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDisplay() { return display; }
    public void setDisplay(String display) { this.display = display; }

    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getDesignations() { return designations; }
    public void setDesignations(String designations) { this.designations = designations; }

    public String getProperties() { return properties; }
    public void setProperties(String properties) { this.properties = properties; }

    public String getParentCode() { return parentCode; }
    public void setParentCode(String parentCode) { this.parentCode = parentCode; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
