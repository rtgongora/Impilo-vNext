package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "msika_fulfillment_policies")
public class FulfillmentPolicyEntity {

    @Id
    @Column(name = "policy_id", length = 26)
    private String policyId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "catalog_item_id", length = 26)
    private String catalogItemId;

    @Column(name = "offering_id", length = 26)
    private String offeringId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_fulfillment_modes", nullable = false, columnDefinition = "jsonb")
    private String allowedFulfillmentModesJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "identity_requirements", nullable = false, columnDefinition = "jsonb")
    private String identityRequirementsJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_constraints", nullable = false, columnDefinition = "jsonb")
    private String deliveryConstraintsJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "substitution_policy", nullable = false, columnDefinition = "jsonb")
    private String substitutionPolicyJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custody_requirements", nullable = false, columnDefinition = "jsonb")
    private String custodyRequirementsJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proof_requirements", nullable = false, columnDefinition = "jsonb")
    private String proofRequirementsJson = "{}";

    @Column(name = "restrictions_summary", columnDefinition = "TEXT")
    private String restrictionsSummary;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (allowedFulfillmentModesJson == null) allowedFulfillmentModesJson = "[]";
        if (identityRequirementsJson == null) identityRequirementsJson = "{}";
        if (deliveryConstraintsJson == null) deliveryConstraintsJson = "{}";
        if (substitutionPolicyJson == null) substitutionPolicyJson = "{}";
        if (custodyRequirementsJson == null) custodyRequirementsJson = "{}";
        if (proofRequirementsJson == null) proofRequirementsJson = "{}";
        if (metadataJson == null) metadataJson = "{}";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getCatalogItemId() { return catalogItemId; }
    public void setCatalogItemId(String catalogItemId) { this.catalogItemId = catalogItemId; }
    public String getOfferingId() { return offeringId; }
    public void setOfferingId(String offeringId) { this.offeringId = offeringId; }
    public String getAllowedFulfillmentModesJson() { return allowedFulfillmentModesJson; }
    public void setAllowedFulfillmentModesJson(String allowedFulfillmentModesJson) { this.allowedFulfillmentModesJson = allowedFulfillmentModesJson; }
    public String getIdentityRequirementsJson() { return identityRequirementsJson; }
    public void setIdentityRequirementsJson(String identityRequirementsJson) { this.identityRequirementsJson = identityRequirementsJson; }
    public String getDeliveryConstraintsJson() { return deliveryConstraintsJson; }
    public void setDeliveryConstraintsJson(String deliveryConstraintsJson) { this.deliveryConstraintsJson = deliveryConstraintsJson; }
    public String getSubstitutionPolicyJson() { return substitutionPolicyJson; }
    public void setSubstitutionPolicyJson(String substitutionPolicyJson) { this.substitutionPolicyJson = substitutionPolicyJson; }
    public String getCustodyRequirementsJson() { return custodyRequirementsJson; }
    public void setCustodyRequirementsJson(String custodyRequirementsJson) { this.custodyRequirementsJson = custodyRequirementsJson; }
    public String getProofRequirementsJson() { return proofRequirementsJson; }
    public void setProofRequirementsJson(String proofRequirementsJson) { this.proofRequirementsJson = proofRequirementsJson; }
    public String getRestrictionsSummary() { return restrictionsSummary; }
    public void setRestrictionsSummary(String restrictionsSummary) { this.restrictionsSummary = restrictionsSummary; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

