package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/** An HPA per-case oversight escalation grant (ROM-W10). The only way HPA sees a council case. */
@Entity @Table(name = "oversight_escalation_grant", schema = "varapi")
public class OversightEscalationGrantEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "council_id", nullable = false) private Long councilId;
    @Column(name = "subject_type", nullable = false, length = 32) private String subjectType;
    @Column(name = "subject_ref", nullable = false, length = 128) private String subjectRef;
    @Column(name = "granted_to_org_id", nullable = false) private UUID grantedToOrgId;
    @Column(name = "reason", columnDefinition = "TEXT") private String reason;
    @Column(name = "granted_by", length = 128) private String grantedBy;
    @Column(name = "valid_from", nullable = false) private OffsetDateTime validFrom;
    @Column(name = "valid_to") private OffsetDateTime validTo;
    @Column(name = "status", nullable = false, length = 20) private String status = "ACTIVE";
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void oc() { createdAt = Instant.now(); if (validFrom == null) validFrom = OffsetDateTime.now(); }
    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID v) { this.tenantId = v; }
    public Long getCouncilId() { return councilId; } public void setCouncilId(Long v) { this.councilId = v; }
    public String getSubjectType() { return subjectType; } public void setSubjectType(String v) { this.subjectType = v; }
    public String getSubjectRef() { return subjectRef; } public void setSubjectRef(String v) { this.subjectRef = v; }
    public UUID getGrantedToOrgId() { return grantedToOrgId; } public void setGrantedToOrgId(UUID v) { this.grantedToOrgId = v; }
    public String getReason() { return reason; } public void setReason(String v) { this.reason = v; }
    public String getGrantedBy() { return grantedBy; } public void setGrantedBy(String v) { this.grantedBy = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
}
