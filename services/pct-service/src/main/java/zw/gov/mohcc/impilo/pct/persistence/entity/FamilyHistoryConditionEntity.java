package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A condition recorded against a relative. See V106__structured_history.sql. */
@Entity
@Table(name = "pct_family_history_conditions")
public class FamilyHistoryConditionEntity {

    @Id
    @Column(name = "condition_id")
    private UUID conditionId;

    @Column(name = "member_id")
    private UUID memberId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "condition_label")
    private String conditionLabel;

    @Column(name = "code")
    private String code;

    @Column(name = "code_system")
    private String codeSystem;

    @Column(name = "onset_age")
    private Integer onsetAge;

    @Column(name = "status")
    private String status;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (conditionId == null) conditionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
    }

    public UUID getConditionId() { return conditionId; }
    public void setConditionId(UUID v) { this.conditionId = v; }
    public UUID getMemberId() { return memberId; }
    public void setMemberId(UUID v) { this.memberId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getConditionLabel() { return conditionLabel; }
    public void setConditionLabel(String v) { this.conditionLabel = v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getCodeSystem() { return codeSystem; }
    public void setCodeSystem(String v) { this.codeSystem = v; }
    public Integer getOnsetAge() { return onsetAge; }
    public void setOnsetAge(Integer v) { this.onsetAge = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
