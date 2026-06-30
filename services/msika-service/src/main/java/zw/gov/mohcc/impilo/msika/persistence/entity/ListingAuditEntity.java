package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable storefront-lane audit record for sensitive/regulated/cross-person/
 * admin actions (listing approval, regulated view, seller validation, Rito link,
 * Khuluma request, policy denial). Complements msika_change_log.
 */
@Entity
@Table(name = "msika_listing_audit")
public class ListingAuditEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "actor_type", length = 32)
    private String actorType;

    @Column(name = "action", nullable = false, length = 48)
    private String action;

    @Column(name = "listing_id", length = 26)
    private String listingId;

    @Column(name = "subject_ref", length = 128)
    private String subjectRef;

    @Column(name = "risk_classification", length = 32)
    private String riskClassification;

    @Column(name = "decision", length = 16)
    private String decision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", nullable = false, columnDefinition = "jsonb")
    private String detailJson = "{}";

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (detailJson == null) detailJson = "{}";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getListingId() { return listingId; }
    public void setListingId(String listingId) { this.listingId = listingId; }
    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }
    public String getRiskClassification() { return riskClassification; }
    public void setRiskClassification(String riskClassification) { this.riskClassification = riskClassification; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
