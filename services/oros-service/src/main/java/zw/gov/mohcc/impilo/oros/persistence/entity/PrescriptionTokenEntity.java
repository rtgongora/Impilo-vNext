package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Signed-reference prescription token (OF-B2, Vol II §13.2): proves the right to
 * retrieve and claim — the clinical content NEVER travels in the token. The QR
 * carries {@code tokenJws} (compact JWS over an opaque payload) and nothing else.
 * A partial unique index enforces at most one ACTIVE token per prescription.
 */
@Entity
@Table(name = "oros_prescription_tokens")
public class PrescriptionTokenEntity {

    /** Token lifecycle: ACTIVE (claimable) → REVOKED (supersession/cancel/fraud) | EXPIRED. */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    @Column(name = "token_id", nullable = false, length = 26)
    private String tokenId;

    @Column(name = "prescription_id", nullable = false, length = 26)
    private String prescriptionId;

    @Column(name = "prescription_version_id", nullable = false, length = 26)
    private String prescriptionVersionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Column(name = "token_jws", columnDefinition = "TEXT", nullable = false)
    private String tokenJws;

    @Column(name = "key_id", length = 128)
    private String keyId;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt = OffsetDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoke_reason", length = 255)
    private String revokeReason;

    public String getTokenId() { return tokenId; }
    public void setTokenId(String tokenId) { this.tokenId = tokenId; }
    public String getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(String prescriptionId) { this.prescriptionId = prescriptionId; }
    public String getPrescriptionVersionId() { return prescriptionVersionId; }
    public void setPrescriptionVersionId(String v) { this.prescriptionVersionId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTokenJws() { return tokenJws; }
    public void setTokenJws(String tokenJws) { this.tokenJws = tokenJws; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
}
