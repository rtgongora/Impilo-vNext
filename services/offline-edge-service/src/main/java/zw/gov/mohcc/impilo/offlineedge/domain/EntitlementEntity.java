package zw.gov.mohcc.impilo.offlineedge.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ofe_entitlements")
public class EntitlementEntity {
    @Id @Column(name = "entitlement_id") private UUID entitlementId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "actor_id", nullable = false) private String actorId;
    @Column(name = "facility_ref", nullable = false) private String facilityRef;
    @Column(name = "workflow_type", nullable = false) private String workflowType = "CAPTURE_VITALS";
    @Column(name = "scope_json", nullable = false, columnDefinition = "TEXT") private String scopeJson;
    @Column(name = "token_hash", nullable = false) private String tokenHash;
    @Column(name = "issued_at", nullable = false) private OffsetDateTime issuedAt;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    @Column(nullable = false) private boolean revoked = false;

    protected EntitlementEntity() {}
    public EntitlementEntity(UUID entitlementId, UUID tenantId, String actorId, String facilityRef,
                              String workflowType, String scopeJson, String tokenHash, OffsetDateTime expiresAt) {
        this.entitlementId = entitlementId; this.tenantId = tenantId; this.actorId = actorId;
        this.facilityRef = facilityRef; this.workflowType = workflowType; this.scopeJson = scopeJson;
        this.tokenHash = tokenHash; this.issuedAt = OffsetDateTime.now(); this.expiresAt = expiresAt;
    }

    public UUID getEntitlementId() { return entitlementId; }
    public UUID getTenantId() { return tenantId; }
    public String getActorId() { return actorId; }
    public String getFacilityRef() { return facilityRef; }
    public String getWorkflowType() { return workflowType; }
    public String getScopeJson() { return scopeJson; }
    public String getTokenHash() { return tokenHash; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean v) { this.revoked = v; }
}
