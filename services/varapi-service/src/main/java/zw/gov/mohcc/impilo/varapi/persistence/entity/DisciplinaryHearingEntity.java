package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A committee hearing on a disciplinary case (ROM-W8). */
@Entity @Table(name = "disciplinary_hearing", schema = "varapi")
public class DisciplinaryHearingEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "case_id", nullable = false) private Long caseId;
    @Column(name = "committee_ref", length = 128) private String committeeRef;
    @Column(name = "status", nullable = false, length = 24) private String status = "DOCKETED";
    @Column(name = "scheduled_at") private OffsetDateTime scheduledAt;
    @Column(name = "determination", columnDefinition = "TEXT") private String determination;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void oc() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void ou() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID v) { this.tenantId = v; }
    public Long getCaseId() { return caseId; } public void setCaseId(Long v) { this.caseId = v; }
    public String getCommitteeRef() { return committeeRef; } public void setCommitteeRef(String v) { this.committeeRef = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; } public void setScheduledAt(OffsetDateTime v) { this.scheduledAt = v; }
    public String getDetermination() { return determination; } public void setDetermination(String v) { this.determination = v; }
}
