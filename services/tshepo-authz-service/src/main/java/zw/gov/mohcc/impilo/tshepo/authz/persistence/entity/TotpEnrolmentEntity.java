package zw.gov.mohcc.impilo.tshepo.authz.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A persisted authenticator-app TOTP enrolment (RFC 6238) for one actor in one tenant.
 * The shared secret is stored AES-256-GCM encrypted at rest ({@code secretCipher} + {@code secretIv}).
 * One enrolment per (tenant, actor).
 */
@Entity
@Table(name = "totp_enrolment", schema = "tshepo_authz")
@IdClass(TotpEnrolmentEntity.Key.class)
public class TotpEnrolmentEntity {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "secret_cipher", nullable = false, columnDefinition = "TEXT")
    private String secretCipher;

    @Column(name = "secret_iv", nullable = false, columnDefinition = "TEXT")
    private String secretIv;

    @Column(name = "algorithm", nullable = false, length = 16)
    private String algorithm = "SHA1";

    @Column(name = "digits", nullable = false)
    private int digits = 6;

    @Column(name = "period", nullable = false)
    private int period = 30;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING_CONFIRM";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getSecretCipher() { return secretCipher; }
    public void setSecretCipher(String secretCipher) { this.secretCipher = secretCipher; }
    public String getSecretIv() { return secretIv; }
    public void setSecretIv(String secretIv) { this.secretIv = secretIv; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public int getDigits() { return digits; }
    public void setDigits(int digits) { this.digits = digits; }
    public int getPeriod() { return period; }
    public void setPeriod(int period) { this.period = period; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }

    /** Composite primary key (tenant, actor). */
    public static class Key implements Serializable {
        private UUID tenantId;
        private String actorId;

        public Key() {}

        public Key(UUID tenantId, String actorId) {
            this.tenantId = tenantId;
            this.actorId = actorId;
        }

        public UUID getTenantId() { return tenantId; }
        public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
        public String getActorId() { return actorId; }
        public void setActorId(String actorId) { this.actorId = actorId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(tenantId, key.tenantId) && Objects.equals(actorId, key.actorId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, actorId);
        }
    }
}
