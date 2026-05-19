package zw.gov.mohcc.impilo.integration.governance.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ih_s2s_contracts")
public class ServiceToServiceContractEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "caller_service_id", length = 128, nullable = false)
    private String callerServiceId;

    @Column(name = "callee_service_id", length = 128, nullable = false)
    private String calleeServiceId;

    @Column(name = "contract_version", length = 32, nullable = false)
    private String contractVersion;

    @Lob
    @Column(name = "allowed_operations", columnDefinition = "TEXT", nullable = false)
    private String allowedOperationsCsv;

    @Lob
    @Column(name = "allowed_scopes", columnDefinition = "TEXT")
    private String allowedScopesCsv;

    @Lob
    @Column(name = "allowed_event_topics", columnDefinition = "TEXT")
    private String allowedEventTopicsCsv;

    @Lob
    @Column(name = "allowed_request_sources", columnDefinition = "TEXT", nullable = false)
    private String allowedRequestSourcesCsv;

    @Column(name = "rate_limit_rpm")
    private Integer rateLimitRpm;

    @Column(name = "rate_limit_burst")
    private Integer rateLimitBurst;

    @Column(name = "owner_team", length = 128, nullable = false)
    private String ownerTeam;

    @Column(name = "support_contact_name", length = 256)
    private String supportContactName;

    @Column(name = "support_contact_email", length = 256)
    private String supportContactEmail;

    @Column(name = "last_verified_at")
    private OffsetDateTime lastVerifiedAt;

    @Column(name = "status", length = 16, nullable = false)
    private String status = "DRAFT";

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "pod_id", length = 64, nullable = false)
    private String podId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCallerServiceId() { return callerServiceId; }
    public void setCallerServiceId(String v) { this.callerServiceId = v; }
    public String getCalleeServiceId() { return calleeServiceId; }
    public void setCalleeServiceId(String v) { this.calleeServiceId = v; }
    public String getContractVersion() { return contractVersion; }
    public void setContractVersion(String v) { this.contractVersion = v; }
    public String getAllowedOperationsCsv() { return allowedOperationsCsv; }
    public void setAllowedOperationsCsv(String v) { this.allowedOperationsCsv = v; }
    public String getAllowedScopesCsv() { return allowedScopesCsv; }
    public void setAllowedScopesCsv(String v) { this.allowedScopesCsv = v; }
    public String getAllowedEventTopicsCsv() { return allowedEventTopicsCsv; }
    public void setAllowedEventTopicsCsv(String v) { this.allowedEventTopicsCsv = v; }
    public String getAllowedRequestSourcesCsv() { return allowedRequestSourcesCsv; }
    public void setAllowedRequestSourcesCsv(String v) { this.allowedRequestSourcesCsv = v; }
    public Integer getRateLimitRpm() { return rateLimitRpm; }
    public void setRateLimitRpm(Integer v) { this.rateLimitRpm = v; }
    public Integer getRateLimitBurst() { return rateLimitBurst; }
    public void setRateLimitBurst(Integer v) { this.rateLimitBurst = v; }
    public String getOwnerTeam() { return ownerTeam; }
    public void setOwnerTeam(String v) { this.ownerTeam = v; }
    public String getSupportContactName() { return supportContactName; }
    public void setSupportContactName(String v) { this.supportContactName = v; }
    public String getSupportContactEmail() { return supportContactEmail; }
    public void setSupportContactEmail(String v) { this.supportContactEmail = v; }
    public OffsetDateTime getLastVerifiedAt() { return lastVerifiedAt; }
    public void setLastVerifiedAt(OffsetDateTime v) { this.lastVerifiedAt = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String v) { this.tenantId = v; }
    public String getPodId() { return podId; }
    public void setPodId(String v) { this.podId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
