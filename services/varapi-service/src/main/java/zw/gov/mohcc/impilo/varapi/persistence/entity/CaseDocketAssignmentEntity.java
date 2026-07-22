package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A committee member's docket assignment to a case (ROM-W8) — the visibility scope. */
@Entity @Table(name = "case_docket_assignment", schema = "varapi")
public class CaseDocketAssignmentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "case_id", nullable = false) private Long caseId;
    @Column(name = "committee_member_health_id", nullable = false, length = 128) private String committeeMemberHealthId;
    @Column(name = "committee_ref", length = 128) private String committeeRef;
    @Column(name = "assigned_at", nullable = false, updatable = false) private OffsetDateTime assignedAt;
    @Column(name = "status", nullable = false, length = 20) private String status = "ACTIVE";
    @PrePersist void oc() { if (assignedAt == null) assignedAt = OffsetDateTime.now(); }
    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID v) { this.tenantId = v; }
    public Long getCaseId() { return caseId; } public void setCaseId(Long v) { this.caseId = v; }
    public String getCommitteeMemberHealthId() { return committeeMemberHealthId; } public void setCommitteeMemberHealthId(String v) { this.committeeMemberHealthId = v; }
    public String getCommitteeRef() { return committeeRef; } public void setCommitteeRef(String v) { this.committeeRef = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
}
