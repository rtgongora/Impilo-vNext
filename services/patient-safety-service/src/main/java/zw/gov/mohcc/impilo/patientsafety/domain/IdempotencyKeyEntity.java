package zw.gov.mohcc.impilo.patientsafety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Records the outcome of a mutating request so a replay carrying the same Idempotency-Key returns
 * the original result instead of acting twice. Keyed by (tenant_id, pod_id, idempotency_key) per the
 * tech-companion spec.
 */
@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKeyEntity.PK.class)
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
    @Id
    @Column(name = "pod_id", nullable = false)
    private String podId;
    @Id
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;
    @Column(name = "response_status", nullable = false)
    private int responseStatus;
    @Column(name = "response_body", nullable = false)
    private String responseBody;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    protected IdempotencyKeyEntity() {}

    public IdempotencyKeyEntity(String tenantId, String podId, String idempotencyKey,
                                String requestHash, int responseStatus, String responseBody) {
        this.tenantId = tenantId;
        this.podId = podId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.createdAt = OffsetDateTime.now();
    }

    public String getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public int getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    /** Composite primary key. */
    public static class PK implements Serializable {
        private String tenantId;
        private String podId;
        private String idempotencyKey;

        public PK() {}

        public PK(String tenantId, String podId, String idempotencyKey) {
            this.tenantId = tenantId;
            this.podId = podId;
            this.idempotencyKey = idempotencyKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(tenantId, pk.tenantId)
                    && Objects.equals(podId, pk.podId)
                    && Objects.equals(idempotencyKey, pk.idempotencyKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, podId, idempotencyKey);
        }
    }
}
