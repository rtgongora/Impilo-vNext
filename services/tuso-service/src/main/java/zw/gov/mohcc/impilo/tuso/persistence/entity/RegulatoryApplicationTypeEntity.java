package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "regulatory_application_type", schema = "tuso")
public class RegulatoryApplicationTypeEntity {

    @Id
    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "engine_type", nullable = false, length = 64)
    private String engineType;

    @Column(name = "route", length = 64)
    private String route;

    @Column(name = "council_review_required", nullable = false)
    private boolean councilReviewRequired;

    @Column(name = "inspection_required", nullable = false)
    private boolean inspectionRequired = true;

    @Column(name = "fee_required", nullable = false)
    private boolean feeRequired = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_requirements", columnDefinition = "jsonb")
    private List<Map<String, Object>> evidenceRequirements;

    @Column(name = "manual_route_summary", columnDefinition = "text")
    private String manualRouteSummary;

    @Column(name = "source_manual", length = 64)
    private String sourceManual;

    @Column(name = "source_page_start")
    private Integer sourcePageStart;

    @Column(name = "source_page_end")
    private Integer sourcePageEnd;

    @Column(name = "validation_status", nullable = false, length = 64)
    private String validationStatus = "REFERENCE_REQUIRES_REGULATOR_VALIDATION";

    @Column(name = "active", nullable = false)
    private boolean active = true;

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
    public String getEngineType() { return engineType; }
    public void setEngineType(String engineType) { this.engineType = engineType; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public boolean isCouncilReviewRequired() { return councilReviewRequired; }
    public void setCouncilReviewRequired(boolean councilReviewRequired) { this.councilReviewRequired = councilReviewRequired; }
    public boolean isInspectionRequired() { return inspectionRequired; }
    public void setInspectionRequired(boolean inspectionRequired) { this.inspectionRequired = inspectionRequired; }
    public boolean isFeeRequired() { return feeRequired; }
    public void setFeeRequired(boolean feeRequired) { this.feeRequired = feeRequired; }
    public List<Map<String, Object>> getEvidenceRequirements() { return evidenceRequirements; }
    public void setEvidenceRequirements(List<Map<String, Object>> evidenceRequirements) { this.evidenceRequirements = evidenceRequirements; }
    public String getManualRouteSummary() { return manualRouteSummary; }
    public void setManualRouteSummary(String manualRouteSummary) { this.manualRouteSummary = manualRouteSummary; }
    public String getSourceManual() { return sourceManual; }
    public void setSourceManual(String sourceManual) { this.sourceManual = sourceManual; }
    public Integer getSourcePageStart() { return sourcePageStart; }
    public void setSourcePageStart(Integer sourcePageStart) { this.sourcePageStart = sourcePageStart; }
    public Integer getSourcePageEnd() { return sourcePageEnd; }
    public void setSourcePageEnd(Integer sourcePageEnd) { this.sourcePageEnd = sourcePageEnd; }
    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
