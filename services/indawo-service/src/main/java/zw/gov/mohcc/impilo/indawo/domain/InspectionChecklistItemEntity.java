package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "ind_checklist_items")
public class InspectionChecklistItemEntity {

    @Id
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "section", nullable = false, length = 128)
    private String section;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "requirement_text", nullable = false, columnDefinition = "TEXT")
    private String requirementText;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity = "MAJOR";

    @Column(name = "evidence_type", length = 32)
    private String evidenceType;

    @Column(name = "pass_criteria", columnDefinition = "TEXT")
    private String passCriteria;

    @Column(name = "critical_flag", nullable = false)
    private boolean criticalFlag = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    protected InspectionChecklistItemEntity() {}

    public InspectionChecklistItemEntity(UUID itemId, UUID templateId, String section, String code, String requirementText) {
        this.itemId = itemId;
        this.templateId = templateId;
        this.section = section;
        this.code = code;
        this.requirementText = requirementText;
    }

    public UUID getItemId() { return itemId; }
    public UUID getTemplateId() { return templateId; }
    public String getSection() { return section; }
    public String getCode() { return code; }
    public String getRequirementText() { return requirementText; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }
    public String getPassCriteria() { return passCriteria; }
    public void setPassCriteria(String passCriteria) { this.passCriteria = passCriteria; }
    public boolean isCriticalFlag() { return criticalFlag; }
    public void setCriticalFlag(boolean criticalFlag) { this.criticalFlag = criticalFlag; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}

