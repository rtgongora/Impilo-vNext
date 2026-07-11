package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "facility_classification", schema = "tuso")
public class FacilityClassificationEntity {

    @Id
    @Column(name = "classification_id", nullable = false, updatable = false)
    private UUID classificationId;

    @Column(name = "class_code", nullable = false, length = 16)
    private String classCode;

    @Column(name = "facility_type_label", nullable = false, length = 255)
    private String facilityTypeLabel;

    @Column(name = "source_manual", length = 64)
    private String sourceManual;

    @Column(name = "source_page")
    private Integer sourcePage;

    @Column(name = "validation_status", nullable = false, length = 64)
    private String validationStatus = "REFERENCE_REQUIRES_REGULATOR_VALIDATION";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (classificationId == null) {
            classificationId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getClassificationId() { return classificationId; }
    public void setClassificationId(UUID classificationId) { this.classificationId = classificationId; }
    public String getClassCode() { return classCode; }
    public void setClassCode(String classCode) { this.classCode = classCode; }
    public String getFacilityTypeLabel() { return facilityTypeLabel; }
    public void setFacilityTypeLabel(String facilityTypeLabel) { this.facilityTypeLabel = facilityTypeLabel; }
    public String getSourceManual() { return sourceManual; }
    public void setSourceManual(String sourceManual) { this.sourceManual = sourceManual; }
    public Integer getSourcePage() { return sourcePage; }
    public void setSourcePage(Integer sourcePage) { this.sourcePage = sourcePage; }
    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
