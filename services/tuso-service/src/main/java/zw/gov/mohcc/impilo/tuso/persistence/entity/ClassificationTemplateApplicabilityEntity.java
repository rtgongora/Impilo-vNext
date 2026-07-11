package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Explicit applicability mapping: facility classification / facility type /
 * regulated unit type → inspection template composition. No facility may
 * silently receive an irrelevant or generic checklist.
 */
@Entity
@Table(name = "classification_template_applicability", schema = "tuso")
public class ClassificationTemplateApplicabilityEntity {

    @Id
    @Column(name = "applicability_id", nullable = false, updatable = false)
    private UUID applicabilityId;

    @Column(name = "class_code", length = 16)
    private String classCode;

    @Column(name = "classification_label", length = 255)
    private String classificationLabel;

    @Column(name = "facility_type", length = 64)
    private String facilityType;

    @Column(name = "unit_type", length = 64)
    private String unitType;

    @Column(name = "composition_code", nullable = false, length = 96)
    private String compositionCode;

    @Column(name = "rationale", columnDefinition = "text")
    private String rationale;

    @Column(name = "source_doc", length = 64)
    private String sourceDoc;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    void prePersist() {
        if (applicabilityId == null) applicabilityId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getApplicabilityId() { return applicabilityId; }
    public void setApplicabilityId(UUID applicabilityId) { this.applicabilityId = applicabilityId; }
    public String getClassCode() { return classCode; }
    public void setClassCode(String classCode) { this.classCode = classCode; }
    public String getClassificationLabel() { return classificationLabel; }
    public void setClassificationLabel(String classificationLabel) { this.classificationLabel = classificationLabel; }
    public String getFacilityType() { return facilityType; }
    public void setFacilityType(String facilityType) { this.facilityType = facilityType; }
    public String getUnitType() { return unitType; }
    public void setUnitType(String unitType) { this.unitType = unitType; }
    public String getCompositionCode() { return compositionCode; }
    public void setCompositionCode(String compositionCode) { this.compositionCode = compositionCode; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getSourceDoc() { return sourceDoc; }
    public void setSourceDoc(String sourceDoc) { this.sourceDoc = sourceDoc; }
    public Instant getCreatedAt() { return createdAt; }
}
