package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "merge_history", schema = "vito")
public class MergeHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "survivor_crid", nullable = false)
    private UUID survivorCrid;

    @Column(name = "merged_crid", nullable = false)
    private UUID mergedCrid;

    @Column(name = "dedup_case_id")
    private Long dedupCaseId;

    @Column(name = "strategy")
    private String strategy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_decisions", columnDefinition = "jsonb")
    private String fieldDecisions;

    @Column(name = "reversible")
    private boolean reversible = true;

    @Column(name = "reversed_at")
    private OffsetDateTime reversedAt;

    @Column(name = "reversed_by")
    private String reversedBy;

    @Column(name = "created_by_id")
    private String createdById;

    @Column(name = "created_by_type")
    private String createdByType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getSurvivorCrid() { return survivorCrid; }
    public void setSurvivorCrid(UUID survivorCrid) { this.survivorCrid = survivorCrid; }
    public UUID getMergedCrid() { return mergedCrid; }
    public void setMergedCrid(UUID mergedCrid) { this.mergedCrid = mergedCrid; }
    public Long getDedupCaseId() { return dedupCaseId; }
    public void setDedupCaseId(Long dedupCaseId) { this.dedupCaseId = dedupCaseId; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public String getFieldDecisions() { return fieldDecisions; }
    public void setFieldDecisions(String fieldDecisions) { this.fieldDecisions = fieldDecisions; }
    public boolean isReversible() { return reversible; }
    public void setReversible(boolean reversible) { this.reversible = reversible; }
    public OffsetDateTime getReversedAt() { return reversedAt; }
    public void setReversedAt(OffsetDateTime reversedAt) { this.reversedAt = reversedAt; }
    public String getReversedBy() { return reversedBy; }
    public void setReversedBy(String reversedBy) { this.reversedBy = reversedBy; }
    public String getCreatedById() { return createdById; }
    public void setCreatedById(String createdById) { this.createdById = createdById; }
    public String getCreatedByType() { return createdByType; }
    public void setCreatedByType(String createdByType) { this.createdByType = createdByType; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
