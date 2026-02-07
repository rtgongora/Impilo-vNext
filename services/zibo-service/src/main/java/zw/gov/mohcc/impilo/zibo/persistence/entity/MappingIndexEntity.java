package zw.gov.mohcc.impilo.zibo.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Flattened index of concept mappings extracted from ConceptMap artifacts.
 * Enables fast cross-coding lookups without parsing the full FHIR resource.
 */
@Entity
@Table(name = "zibo_mappings_index")
public class MappingIndexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "mapping_id", nullable = false)
    private UUID mappingId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source_system", nullable = false)
    private String sourceSystem;

    @Column(name = "source_code", nullable = false)
    private String sourceCode;

    @Column(name = "source_display")
    private String sourceDisplay;

    @Column(name = "target_system", nullable = false)
    private String targetSystem;

    @Column(name = "target_code", nullable = false)
    private String targetCode;

    @Column(name = "target_display")
    private String targetDisplay;

    @Column(name = "equivalence")
    private String equivalence = "equivalent";

    @Column(name = "conceptmap_ref")
    private UUID conceptmapRef;

    @Column(name = "confidence")
    private BigDecimal confidence = new BigDecimal("1.00");

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // getId/setId aliases

    public UUID getId() { return mappingId; }
    public void setId(UUID id) { this.mappingId = id; }

    // Getters and setters

    public UUID getMappingId() { return mappingId; }
    public void setMappingId(UUID mappingId) { this.mappingId = mappingId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public String getSourceDisplay() { return sourceDisplay; }
    public void setSourceDisplay(String sourceDisplay) { this.sourceDisplay = sourceDisplay; }

    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String targetSystem) { this.targetSystem = targetSystem; }

    public String getTargetCode() { return targetCode; }
    public void setTargetCode(String targetCode) { this.targetCode = targetCode; }

    public String getTargetDisplay() { return targetDisplay; }
    public void setTargetDisplay(String targetDisplay) { this.targetDisplay = targetDisplay; }

    public String getEquivalence() { return equivalence; }
    public void setEquivalence(String equivalence) { this.equivalence = equivalence; }

    public UUID getConceptmapRef() { return conceptmapRef; }
    public void setConceptmapRef(UUID conceptmapRef) { this.conceptmapRef = conceptmapRef; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
