package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "patient_share_access_grant", schema = "vito")
public class PatientShareAccessGrantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "share_request_id", nullable = false)
    private Long shareRequestId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "health_id", nullable = false)
    private UUID healthId;

    @Column(name = "scope_type", nullable = false, length = 64)
    private String scopeType;

    @Column(name = "allowed_actions")
    private String allowedActions;

    @Column(name = "visibility_profile", length = 64)
    private String visibilityProfile;

    @Column(name = "update_permissions")
    private String updatePermissions;

    @Column(name = "issued_by_policy_ref", length = 128)
    private String issuedByPolicyRef;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "trust_level", length = 64)
    private String trustLevel;

    @Column(name = "external_profile_id")
    private Long externalProfileId;

    @Column(name = "temporary_provider_public_id", length = 26)
    private String temporaryProviderPublicId;

    @Column(name = "start_at")
    private OffsetDateTime startAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "session_activated_count", nullable = false)
    private int sessionActivatedCount = 0;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getShareRequestId() {
        return shareRequestId;
    }

    public void setShareRequestId(Long shareRequestId) {
        this.shareRequestId = shareRequestId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getHealthId() {
        return healthId;
    }

    public void setHealthId(UUID healthId) {
        this.healthId = healthId;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getAllowedActions() {
        return allowedActions;
    }

    public void setAllowedActions(String allowedActions) {
        this.allowedActions = allowedActions;
    }

    public String getVisibilityProfile() {
        return visibilityProfile;
    }

    public void setVisibilityProfile(String visibilityProfile) {
        this.visibilityProfile = visibilityProfile;
    }

    public String getUpdatePermissions() {
        return updatePermissions;
    }

    public void setUpdatePermissions(String updatePermissions) {
        this.updatePermissions = updatePermissions;
    }

    public String getIssuedByPolicyRef() {
        return issuedByPolicyRef;
    }

    public void setIssuedByPolicyRef(String issuedByPolicyRef) {
        this.issuedByPolicyRef = issuedByPolicyRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTrustLevel() {
        return trustLevel;
    }

    public void setTrustLevel(String trustLevel) {
        this.trustLevel = trustLevel;
    }

    public Long getExternalProfileId() {
        return externalProfileId;
    }

    public void setExternalProfileId(Long externalProfileId) {
        this.externalProfileId = externalProfileId;
    }

    public String getTemporaryProviderPublicId() {
        return temporaryProviderPublicId;
    }

    public void setTemporaryProviderPublicId(String temporaryProviderPublicId) {
        this.temporaryProviderPublicId = temporaryProviderPublicId;
    }

    public OffsetDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(OffsetDateTime startAt) {
        this.startAt = startAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public int getSessionActivatedCount() {
        return sessionActivatedCount;
    }

    public void setSessionActivatedCount(int sessionActivatedCount) {
        this.sessionActivatedCount = sessionActivatedCount;
    }
}
