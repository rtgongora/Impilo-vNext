package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** RFI (request-for-information) on a provider application (ROM-W4, R7). */
@Entity
@Table(name = "application_information_request", schema = "varapi")
public class ApplicationInformationRequestEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "application_id", nullable = false) private Long applicationId;
    @Column(name = "requested_by", length = 128) private String requestedBy;
    @Column(name = "message", nullable = false, columnDefinition = "TEXT") private String message;
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(name = "status", nullable = false, length = 20) private String status = "OPEN";
    @Column(name = "response_notes", columnDefinition = "TEXT") private String responseNotes;
    @Column(name = "responded_at") private OffsetDateTime respondedAt;
    @Column(name = "responded_by", length = 128) private String respondedBy;
    @Column(name = "closed_at") private OffsetDateTime closedAt;
    @Column(name = "closed_by", length = 128) private String closedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void oc() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void ou() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID t) { this.tenantId = t; }
    public Long getApplicationId() { return applicationId; } public void setApplicationId(Long a) { this.applicationId = a; }
    public String getRequestedBy() { return requestedBy; } public void setRequestedBy(String v) { this.requestedBy = v; }
    public String getMessage() { return message; } public void setMessage(String v) { this.message = v; }
    public LocalDate getDueDate() { return dueDate; } public void setDueDate(LocalDate v) { this.dueDate = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public String getResponseNotes() { return responseNotes; } public void setResponseNotes(String v) { this.responseNotes = v; }
    public OffsetDateTime getRespondedAt() { return respondedAt; } public void setRespondedAt(OffsetDateTime v) { this.respondedAt = v; }
    public String getRespondedBy() { return respondedBy; } public void setRespondedBy(String v) { this.respondedBy = v; }
    public OffsetDateTime getClosedAt() { return closedAt; } public void setClosedAt(OffsetDateTime v) { this.closedAt = v; }
    public String getClosedBy() { return closedBy; } public void setClosedBy(String v) { this.closedBy = v; }
}
