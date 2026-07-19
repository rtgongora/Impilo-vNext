package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A provider badge credential (PJ6, D-P5): serial + signed QR payload. The
 * badge SELECTS an account (tap-to-login prefill, public verification) and
 * never authorises anything by itself; losing it revokes the serial only.
 */
@Entity
@Table(name = "provider_badge", schema = "varapi")
public class ProviderBadgeEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String STATUS_LOST = "LOST";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "badge_serial", nullable = false, length = 40)
    private String badgeSerial;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_ACTIVE;

    @Column(name = "print_job_ref", length = 120)
    private String printJobRef;

    @Column(name = "issued_by")
    private String issuedBy;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason")
    private String revokeReason;

    @PrePersist
    protected void onCreate() {
        issuedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getBadgeSerial() { return badgeSerial; }
    public void setBadgeSerial(String badgeSerial) { this.badgeSerial = badgeSerial; }
    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPrintJobRef() { return printJobRef; }
    public void setPrintJobRef(String printJobRef) { this.printJobRef = printJobRef; }
    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
}
