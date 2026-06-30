package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An audit record of a single failed sync attempt.
 */
@Entity
@Table(name = "inv_external_sync_errors")
public class ExternalSyncErrorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "error_id", nullable = false)
    private UUID errorId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "sync_id", nullable = false)
    private UUID syncId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getErrorId() { return errorId; }
    public void setErrorId(UUID errorId) { this.errorId = errorId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getSyncId() { return syncId; }
    public void setSyncId(UUID syncId) { this.syncId = syncId; }

    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
