package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rit_audit_section", schema = "rito")
public class AuditSectionEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "audit_tool_id", nullable = false)
    private UUID auditToolId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "section_key", nullable = false, length = 128)
    private String sectionKey;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;

    @Column(name = "weight", nullable = false)
    private BigDecimal weight;

    @Column(name = "max_score")
    private BigDecimal maxScore;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAuditToolId() { return auditToolId; }
    public void setAuditToolId(UUID auditToolId) { this.auditToolId = auditToolId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSectionKey() { return sectionKey; }
    public void setSectionKey(String sectionKey) { this.sectionKey = sectionKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getOrdinal() { return ordinal; }
    public void setOrdinal(Integer ordinal) { this.ordinal = ordinal; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public BigDecimal getMaxScore() { return maxScore; }
    public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
