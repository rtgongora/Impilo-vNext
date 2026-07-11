package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "inspection_response", schema = "tuso")
public class InspectionResponseEntity {

    @Id
    @Column(name = "response_id", nullable = false, updatable = false)
    private UUID responseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "template_version")
    private Integer templateVersion;

    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(name = "item_section", length = 128)
    private String itemSection;

    @Column(name = "requirement_text", columnDefinition = "text")
    private String requirementText;

    @Column(name = "response", nullable = false, length = 32)
    private String response;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "measurement", columnDefinition = "jsonb")
    private Map<String, Object> measurement;

    @Column(name = "comments", columnDefinition = "text")
    private String comments;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", columnDefinition = "jsonb")
    private List<Map<String, Object>> evidenceRefs;

    @Column(name = "inspector_id", nullable = false, length = 255)
    private String inspectorId;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (responseId == null) {
            responseId = UUID.randomUUID();
        }
        if (capturedAt == null) {
            capturedAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getResponseId() { return responseId; }
    public void setResponseId(UUID responseId) { this.responseId = responseId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getVisitId() { return visitId; }
    public void setVisitId(UUID visitId) { this.visitId = visitId; }
    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; }
    public Integer getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(Integer templateVersion) { this.templateVersion = templateVersion; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemSection() { return itemSection; }
    public void setItemSection(String itemSection) { this.itemSection = itemSection; }
    public String getRequirementText() { return requirementText; }
    public void setRequirementText(String requirementText) { this.requirementText = requirementText; }
    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public Map<String, Object> getMeasurement() { return measurement; }
    public void setMeasurement(Map<String, Object> measurement) { this.measurement = measurement; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public List<Map<String, Object>> getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(List<Map<String, Object>> evidenceRefs) { this.evidenceRefs = evidenceRefs; }
    public String getInspectorId() { return inspectorId; }
    public void setInspectorId(String inspectorId) { this.inspectorId = inspectorId; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
