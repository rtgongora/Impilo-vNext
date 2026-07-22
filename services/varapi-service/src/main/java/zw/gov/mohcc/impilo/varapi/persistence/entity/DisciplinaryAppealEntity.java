package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** An appeal referencing a disciplinary determination (ROM-W8). */
@Entity @Table(name = "disciplinary_appeal", schema = "varapi")
public class DisciplinaryAppealEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "case_id", nullable = false) private Long caseId;
    @Column(name = "appealed_decision", length = 128) private String appealedDecision;
    @Column(name = "grounds", columnDefinition = "TEXT") private String grounds;
    @Column(name = "filed_by", length = 128) private String filedBy;
    @Column(name = "status", nullable = false, length = 24) private String status = "FILED";
    @Column(name = "filed_at", nullable = false, updatable = false) private OffsetDateTime filedAt;
    @PrePersist void oc() { if (filedAt == null) filedAt = OffsetDateTime.now(); }
    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID v) { this.tenantId = v; }
    public Long getCaseId() { return caseId; } public void setCaseId(Long v) { this.caseId = v; }
    public String getAppealedDecision() { return appealedDecision; } public void setAppealedDecision(String v) { this.appealedDecision = v; }
    public String getGrounds() { return grounds; } public void setGrounds(String v) { this.grounds = v; }
    public String getFiledBy() { return filedBy; } public void setFiledBy(String v) { this.filedBy = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
}
