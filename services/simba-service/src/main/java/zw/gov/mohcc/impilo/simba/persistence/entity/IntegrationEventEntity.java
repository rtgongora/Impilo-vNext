package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Handoff ledger row. When a wellness journey needs an owner (KHULUMA/FUNDO/OROS/NOMPILO/PCT),
 * Simba records the intent here (PENDING/UNSUPPORTED) and emits an event — it never fakes success.
 * The row is updated to DISPATCHED/ACKED/FAILED as the handoff progresses.
 */
@Entity
@Table(name = "simba_integration_event", schema = "simba")
public class IntegrationEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "integration_event_id", nullable = false, unique = true)
    private UUID integrationEventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "person_cpid", length = 128)
    private String personCpid;

    @Column(name = "target_owner", nullable = false, length = 24)
    private String targetOwner;

    @Column(name = "intent", nullable = false, length = 64)
    private String intent;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "PENDING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    @Column(name = "response_ref", length = 128)
    private String responseRef;

    @Column(name = "error", length = 512)
    private String error;

    @Column(name = "source_ref", length = 128)
    private String sourceRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (integrationEventId == null) {
            integrationEventId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getIntegrationEventId() { return integrationEventId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPersonCpid() { return personCpid; }
    public void setPersonCpid(String personCpid) { this.personCpid = personCpid; }
    public String getTargetOwner() { return targetOwner; }
    public void setTargetOwner(String targetOwner) { this.targetOwner = targetOwner; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestPayload() { return requestPayload; }
    public void setRequestPayload(String requestPayload) { this.requestPayload = requestPayload; }
    public String getResponseRef() { return responseRef; }
    public void setResponseRef(String responseRef) { this.responseRef = responseRef; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
