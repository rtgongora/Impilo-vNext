package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.msikaflow.domain.DeliveryMode;
import zw.gov.mohcc.impilo.msikaflow.domain.DeliveryStatus;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mf_delivery_plans")
public class DeliveryPlanEntity {

    @Id
    @Column(name = "plan_id", nullable = false, length = 26)
    private String planId;

    @Column(name = "fulfillment_id", nullable = false, length = 26)
    private String fulfillmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 40)
    private DeliveryMode mode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "origin", nullable = false, columnDefinition = "jsonb")
    private String originJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "destination", nullable = false, columnDefinition = "jsonb")
    private String destinationJson = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryStatus status = DeliveryStatus.PLANNED;

    @Column(name = "selected_provider_ref", length = 255)
    private String selectedProviderRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "estimated_window", columnDefinition = "jsonb")
    private String estimatedWindowJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "constraints_summary", columnDefinition = "jsonb")
    private String constraintsSummaryJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (originJson == null) originJson = "{}";
        if (destinationJson == null) destinationJson = "{}";
        if (estimatedWindowJson == null) estimatedWindowJson = "{}";
        if (constraintsSummaryJson == null) constraintsSummaryJson = "{}";
        if (metadataJson == null) metadataJson = "{}";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getFulfillmentId() { return fulfillmentId; }
    public void setFulfillmentId(String fulfillmentId) { this.fulfillmentId = fulfillmentId; }
    public DeliveryMode getMode() { return mode; }
    public void setMode(DeliveryMode mode) { this.mode = mode; }
    public String getOriginJson() { return originJson; }
    public void setOriginJson(String originJson) { this.originJson = originJson; }
    public String getDestinationJson() { return destinationJson; }
    public void setDestinationJson(String destinationJson) { this.destinationJson = destinationJson; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public String getSelectedProviderRef() { return selectedProviderRef; }
    public void setSelectedProviderRef(String selectedProviderRef) { this.selectedProviderRef = selectedProviderRef; }
    public String getEstimatedWindowJson() { return estimatedWindowJson; }
    public void setEstimatedWindowJson(String estimatedWindowJson) { this.estimatedWindowJson = estimatedWindowJson; }
    public String getConstraintsSummaryJson() { return constraintsSummaryJson; }
    public void setConstraintsSummaryJson(String constraintsSummaryJson) { this.constraintsSummaryJson = constraintsSummaryJson; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

