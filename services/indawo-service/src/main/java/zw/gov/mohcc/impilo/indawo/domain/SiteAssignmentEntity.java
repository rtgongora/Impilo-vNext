package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ind_site_assignments")
public class SiteAssignmentEntity {

    @Id
    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "assignment_type", nullable = false, length = 64)
    private String assignmentType;

    @Column(name = "assigned_role", length = 64)
    private String assignedRole;

    @Column(name = "assigned_to_ref", length = 255)
    private String assignedToRef;

    /** Impilo / Health ID when the assignee is a person (see Experience Doctrine). */
    @Column(name = "impilo_health_id")
    private UUID impiloHealthId;

    @Column(name = "jurisdiction_ref", length = 255)
    private String jurisdictionRef;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "OPEN";

    @Column(name = "priority", nullable = false, length = 16)
    private String priority = "NORMAL";

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SiteAssignmentEntity() {}

    public SiteAssignmentEntity(UUID assignmentId, UUID tenantId, UUID siteId, String assignmentType) {
        this.assignmentId = assignmentId;
        this.tenantId = tenantId;
        this.siteId = siteId;
        this.assignmentType = assignmentType;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getAssignmentId() { return assignmentId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSiteId() { return siteId; }
    public String getAssignmentType() { return assignmentType; }
    public String getAssignedRole() { return assignedRole; }
    public void setAssignedRole(String assignedRole) { this.assignedRole = assignedRole; touch(); }
    public String getAssignedToRef() { return assignedToRef; }
    public void setAssignedToRef(String assignedToRef) { this.assignedToRef = assignedToRef; touch(); }
    public UUID getImpiloHealthId() { return impiloHealthId; }
    public void setImpiloHealthId(UUID impiloHealthId) { this.impiloHealthId = impiloHealthId; touch(); }
    public String getJurisdictionRef() { return jurisdictionRef; }
    public void setJurisdictionRef(String jurisdictionRef) { this.jurisdictionRef = jurisdictionRef; touch(); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; touch(); }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; touch(); }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; touch(); }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    private void touch() { this.updatedAt = OffsetDateTime.now(); }
}

