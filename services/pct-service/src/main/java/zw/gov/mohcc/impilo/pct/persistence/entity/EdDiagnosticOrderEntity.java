package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * PCT-side link between an OROS diagnostic order and the trauma episode (W5). PCT places the order on
 * OROS with encounter_ref = trauma_episode_id and keeps this row; it is the authoritative correlation
 * used to reconcile an OROS CRITICAL result back onto the episode (the OROS critical event omits
 * encounter_ref).
 */
@Entity
@Table(name = "ed_diagnostic_order",
        uniqueConstraints = @UniqueConstraint(name = "uq_ed_diagnostic_order_oros",
                columnNames = {"tenant_id", "oros_order_id"}))
public class EdDiagnosticOrderEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "visit_id") private UUID visitId;
    @Column(name = "trauma_episode_id") private UUID traumaEpisodeId;
    @Column(name = "oros_order_id", nullable = false, length = 64) private String orosOrderId;
    @Column(name = "order_type", length = 24) private String orderType;
    @Column(name = "test_code", length = 64) private String testCode;
    @Column(name = "status", nullable = false, length = 24) private String status = "PLACED";
    @Column(name = "oros_result_id", length = 64) private String orosResultId;
    @Column(name = "critical_reason", columnDefinition = "TEXT") private String criticalReason;
    @Column(name = "acked_at") private OffsetDateTime ackedAt;
    @Column(name = "safety_emitted_at") private OffsetDateTime safetyEmittedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getVisitId() { return visitId; }
    public void setVisitId(UUID v) { this.visitId = v; }
    public UUID getTraumaEpisodeId() { return traumaEpisodeId; }
    public void setTraumaEpisodeId(UUID v) { this.traumaEpisodeId = v; }
    public String getOrosOrderId() { return orosOrderId; }
    public void setOrosOrderId(String v) { this.orosOrderId = v; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String v) { this.orderType = v; }
    public String getTestCode() { return testCode; }
    public void setTestCode(String v) { this.testCode = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getOrosResultId() { return orosResultId; }
    public void setOrosResultId(String v) { this.orosResultId = v; }
    public String getCriticalReason() { return criticalReason; }
    public void setCriticalReason(String v) { this.criticalReason = v; }
    public OffsetDateTime getAckedAt() { return ackedAt; }
    public void setAckedAt(OffsetDateTime v) { this.ackedAt = v; }
    public OffsetDateTime getSafetyEmittedAt() { return safetyEmittedAt; }
    public void setSafetyEmittedAt(OffsetDateTime v) { this.safetyEmittedAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
