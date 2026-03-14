package zw.gov.mohcc.impilo.support.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sup_tickets")
public class TicketEntity {
    @Id @Column(name = "ticket_id") private UUID ticketId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 500) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(nullable = false, length = 64) private String category = "GENERAL";
    @Column(nullable = false, length = 16) private String priority = "MEDIUM";
    @Column(nullable = false, length = 32) private String status = "OPEN";
    @Column(name = "reporter_ref", nullable = false) private String reporterRef;
    @Column(name = "assignee_ref") private String assigneeRef;
    @Column(name = "facility_ref") private String facilityRef;
    @Column(columnDefinition = "TEXT") private String resolution;
    @Column(name = "metadata_json", columnDefinition = "TEXT") private String metadataJson;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "resolved_at") private OffsetDateTime resolvedAt;
    @Column(nullable = false) private int version = 1;

    protected TicketEntity() {}
    public TicketEntity(UUID ticketId, UUID tenantId, String title, String description, String reporterRef) {
        this.ticketId = ticketId; this.tenantId = tenantId; this.title = title;
        this.description = description; this.reporterRef = reporterRef;
        OffsetDateTime now = OffsetDateTime.now(); this.createdAt = now; this.updatedAt = now;
    }

    public UUID getTicketId() { return ticketId; }
    public UUID getTenantId() { return tenantId; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { this.priority = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getReporterRef() { return reporterRef; }
    public String getAssigneeRef() { return assigneeRef; }
    public void setAssigneeRef(String v) { this.assigneeRef = v; }
    public String getFacilityRef() { return facilityRef; }
    public void setFacilityRef(String v) { this.facilityRef = v; }
    public String getResolution() { return resolution; }
    public void setResolution(String v) { this.resolution = v; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String v) { this.metadataJson = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime v) { this.resolvedAt = v; }
    public int getVersion() { return version; }
    public void setVersion(int v) { this.version = v; }
}
