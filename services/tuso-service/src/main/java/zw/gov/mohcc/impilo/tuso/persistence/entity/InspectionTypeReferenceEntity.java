package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "inspection_type_reference", schema = "tuso")
public class InspectionTypeReferenceEntity {

    @Id
    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "purpose", columnDefinition = "text")
    private String purpose;

    @Column(name = "source_manual", length = 64)
    private String sourceManual;

    @Column(name = "source_page")
    private Integer sourcePage;

    @Column(name = "validation_status", nullable = false, length = 64)
    private String validationStatus = "REFERENCE_REQUIRES_REGULATOR_VALIDATION";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getSourceManual() { return sourceManual; }
    public void setSourceManual(String sourceManual) { this.sourceManual = sourceManual; }
    public Integer getSourcePage() { return sourcePage; }
    public void setSourcePage(Integer sourcePage) { this.sourcePage = sourcePage; }
    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }
    public Instant getCreatedAt() { return createdAt; }
}
