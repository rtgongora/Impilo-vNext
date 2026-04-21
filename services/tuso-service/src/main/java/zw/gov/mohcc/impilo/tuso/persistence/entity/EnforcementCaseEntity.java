package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "enforcement_case", schema = "tuso")
public class EnforcementCaseEntity {

    @Id
    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private FacilityEntity facility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private FacilityApplicationEntity application;

    @Column(name = "trigger_type", nullable = false, length = 64)
    private String triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 64)
    private EnforcementCaseStatus status = EnforcementCaseStatus.OPEN;

    @Column(name = "recommendation", length = 128)
    private String recommendation;

    @Column(name = "authority_route", length = 128)
    private String authorityRoute;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "linked_inspection_ids", columnDefinition = "jsonb")
    private List<String> linkedInspectionIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "linked_evidence", columnDefinition = "jsonb")
    private List<Map<String, Object>> linkedEvidence;

    @Column(name = "closure_reason", columnDefinition = "text")
    private String closureReason;

    @Column(name = "decision_details", columnDefinition = "text")
    private String decisionDetails;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "opened_by", nullable = false, length = 255)
    private String openedBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (caseId == null) {
            caseId = UUID.randomUUID();
        }
        if (openedAt == null) {
            openedAt = Instant.now();
        }
    }

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }
    public FacilityApplicationEntity getApplication() { return application; }
    public void setApplication(FacilityApplicationEntity application) { this.application = application; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public EnforcementCaseStatus getStatus() { return status; }
    public void setStatus(EnforcementCaseStatus status) { this.status = status; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public String getAuthorityRoute() { return authorityRoute; }
    public void setAuthorityRoute(String authorityRoute) { this.authorityRoute = authorityRoute; }
    public List<String> getLinkedInspectionIds() { return linkedInspectionIds; }
    public void setLinkedInspectionIds(List<String> linkedInspectionIds) { this.linkedInspectionIds = linkedInspectionIds; }
    public List<Map<String, Object>> getLinkedEvidence() { return linkedEvidence; }
    public void setLinkedEvidence(List<Map<String, Object>> linkedEvidence) { this.linkedEvidence = linkedEvidence; }
    public String getClosureReason() { return closureReason; }
    public void setClosureReason(String closureReason) { this.closureReason = closureReason; }
    public String getDecisionDetails() { return decisionDetails; }
    public void setDecisionDetails(String decisionDetails) { this.decisionDetails = decisionDetails; }
    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public String getOpenedBy() { return openedBy; }
    public void setOpenedBy(String openedBy) { this.openedBy = openedBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
