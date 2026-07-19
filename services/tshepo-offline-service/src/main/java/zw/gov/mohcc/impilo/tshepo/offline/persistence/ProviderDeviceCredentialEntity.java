package zw.gov.mohcc.impilo.tshepo.offline.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Device-bound provider offline credential (PJ13, D-P9): issued only from an
 * online session, carrying a cached provider/facility/role status snapshot.
 * Offline login validates against the snapshot; high-risk actions stay online;
 * no new professional identity is created offline.
 */
@Entity
@Table(name = "provider_device_credential", schema = "tshepo_offline")
public class ProviderDeviceCredentialEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_public_id", nullable = false, length = 40)
    private String providerPublicId;

    @Column(name = "person_health_id", nullable = false, length = 128)
    private String personHealthId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "status_snapshot", nullable = false, columnDefinition = "jsonb")
    private String statusSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "high_risk_denylist", nullable = false, columnDefinition = "jsonb")
    private String highRiskDenylist;

    @Column(name = "issued_from_session", nullable = false)
    private String issuedFromSession;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_ACTIVE;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getProviderPublicId() { return providerPublicId; }
    public void setProviderPublicId(String providerPublicId) { this.providerPublicId = providerPublicId; }
    public String getPersonHealthId() { return personHealthId; }
    public void setPersonHealthId(String personHealthId) { this.personHealthId = personHealthId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getStatusSnapshot() { return statusSnapshot; }
    public void setStatusSnapshot(String statusSnapshot) { this.statusSnapshot = statusSnapshot; }
    public String getHighRiskDenylist() { return highRiskDenylist; }
    public void setHighRiskDenylist(String highRiskDenylist) { this.highRiskDenylist = highRiskDenylist; }
    public String getIssuedFromSession() { return issuedFromSession; }
    public void setIssuedFromSession(String issuedFromSession) { this.issuedFromSession = issuedFromSession; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
