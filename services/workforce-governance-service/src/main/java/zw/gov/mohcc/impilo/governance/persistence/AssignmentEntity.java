package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "wgv_assignment")
public class AssignmentEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, length = 255)
    private String subjectId;

    @Column(name = "role_definition_id", nullable = false)
    private UUID roleDefinitionId;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 128)
    private String targetId;

    @Column(name = "organisation_id")
    private UUID organisationId;

    @Column(name = "organisation_unit_id")
    private UUID organisationUnitId;

    @Column(name = "jurisdiction_id")
    private UUID jurisdictionId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "primary_flag", nullable = false)
    private boolean primaryFlag;

    @Column(name = "secondary_flag", nullable = false)
    private boolean secondaryFlag;

    @Column(name = "scope_summary", columnDefinition = "TEXT")
    private String scopeSummary;

    @Column(name = "authority_level", length = 64)
    private String authorityLevel;

    @Column(name = "reporting_line_ref", length = 255)
    private String reportingLineRef;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "extension_json", columnDefinition = "TEXT")
    private String extensionJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AssignmentEntity() {}

    public AssignmentEntity(UUID id, UUID tenantId, String subjectType, String subjectId, UUID roleDefinitionId,
                            String targetType, String targetId, String status) {
        this.id = id;
        this.tenantId = tenantId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.roleDefinitionId = roleDefinitionId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.status = status;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getSubjectType() { return subjectType; }
    public String getSubjectId() { return subjectId; }
    public UUID getRoleDefinitionId() { return roleDefinitionId; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; touch(); }
    public UUID getOrganisationUnitId() { return organisationUnitId; }
    public void setOrganisationUnitId(UUID organisationUnitId) { this.organisationUnitId = organisationUnitId; touch(); }
    public UUID getJurisdictionId() { return jurisdictionId; }
    public void setJurisdictionId(UUID jurisdictionId) { this.jurisdictionId = jurisdictionId; touch(); }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; touch(); }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; touch(); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }
    public boolean isPrimaryFlag() { return primaryFlag; }
    public void setPrimaryFlag(boolean primaryFlag) { this.primaryFlag = primaryFlag; touch(); }
    public boolean isSecondaryFlag() { return secondaryFlag; }
    public void setSecondaryFlag(boolean secondaryFlag) { this.secondaryFlag = secondaryFlag; touch(); }
    public String getScopeSummary() { return scopeSummary; }
    public void setScopeSummary(String scopeSummary) { this.scopeSummary = scopeSummary; touch(); }
    public String getAuthorityLevel() { return authorityLevel; }
    public void setAuthorityLevel(String authorityLevel) { this.authorityLevel = authorityLevel; touch(); }
    public String getReportingLineRef() { return reportingLineRef; }
    public void setReportingLineRef(String reportingLineRef) { this.reportingLineRef = reportingLineRef; touch(); }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; touch(); }
    public String getExtensionJson() { return extensionJson; }
    public void setExtensionJson(String extensionJson) { this.extensionJson = extensionJson; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
