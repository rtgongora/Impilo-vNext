package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rit_audit_finding", schema = "rito")
public class AuditFindingEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "audit_id", nullable = false)
    private UUID auditId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "section_key", length = 128)
    private String sectionKey;

    @Column(name = "item_ref", length = 128)
    private String itemRef;

    @Column(name = "finding_type", nullable = false, length = 32)
    private String findingType;

    @Column(name = "severity", length = 32)
    private String severity;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "recommended_action", columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(name = "score")
    private BigDecimal score;

    @Column(name = "corrective_action_id")
    private UUID correctiveActionId;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAuditId() { return auditId; }
    public void setAuditId(UUID auditId) { this.auditId = auditId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSectionKey() { return sectionKey; }
    public void setSectionKey(String sectionKey) { this.sectionKey = sectionKey; }
    public String getItemRef() { return itemRef; }
    public void setItemRef(String itemRef) { this.itemRef = itemRef; }
    public String getFindingType() { return findingType; }
    public void setFindingType(String findingType) { this.findingType = findingType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public UUID getCorrectiveActionId() { return correctiveActionId; }
    public void setCorrectiveActionId(UUID correctiveActionId) { this.correctiveActionId = correctiveActionId; }
    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
