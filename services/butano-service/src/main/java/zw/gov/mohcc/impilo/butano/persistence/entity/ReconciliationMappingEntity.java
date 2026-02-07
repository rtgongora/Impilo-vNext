package zw.gov.mohcc.impilo.butano.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the butano_reconciliation_mapping table.
 *
 * <p>Tracks O-CPID (provisional/operational CPID) to CPID (permanent)
 * rewrite operations. When a patient's identity is reconciled in VITO,
 * BUTANO must rewrite all FHIR resource references from the old
 * provisional identifier to the new permanent CPID.</p>
 *
 * <p>Status lifecycle: PENDING -> IN_PROGRESS -> COMPLETED | FAILED</p>
 *
 * <p>The unique constraint on (tenant_id, old_cpid) prevents duplicate
 * reconciliation requests for the same provisional identifier within
 * a tenant.</p>
 */
@Entity
@Table(name = "butano_reconciliation_mapping")
public class ReconciliationMappingEntity {

    /** Status: reconciliation request received, not yet started. */
    public static final String STATUS_PENDING = "PENDING";
    /** Status: reconciliation actively rewriting FHIR resources. */
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** Status: all resources successfully rewritten. */
    public static final String STATUS_COMPLETED = "COMPLETED";
    /** Status: reconciliation encountered an unrecoverable error. */
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "old_cpid", nullable = false, length = 128)
    private String oldCpid;

    @Column(name = "new_cpid", nullable = false, length = 128)
    private String newCpid;

    @Column(name = "status", nullable = false, length = 32)
    private String status = STATUS_PENDING;

    @Column(name = "resources_updated", nullable = false)
    private int resourcesUpdated = 0;

    @Column(name = "requested_by", nullable = false, length = 128)
    private String requestedBy;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = OffsetDateTime.now();
        }
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getOldCpid() { return oldCpid; }
    public void setOldCpid(String oldCpid) { this.oldCpid = oldCpid; }

    public String getNewCpid() { return newCpid; }
    public void setNewCpid(String newCpid) { this.newCpid = newCpid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getResourcesUpdated() { return resourcesUpdated; }
    public void setResourcesUpdated(int resourcesUpdated) { this.resourcesUpdated = resourcesUpdated; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public OffsetDateTime getRequestedAt() { return requestedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
