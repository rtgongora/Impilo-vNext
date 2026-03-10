package zw.gov.mohcc.impilo.channels.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ch_assisted_interactions")
public class AssistedInteractionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId;

    @Column(name = "agent_id", nullable = false, length = 255)
    private String agentId;

    @Column(name = "client_id", length = 255)
    private String clientId;

    @Column(name = "notes_json", columnDefinition = "TEXT")
    private String notesJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AssistedInteractionEntity() {}

    public AssistedInteractionEntity(UUID sessionId, String tenantId, String podId,
                                     String agentId) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.podId = podId;
        this.agentId = agentId;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public String getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public String getAgentId() { return agentId; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getNotesJson() { return notesJson; }
    public void setNotesJson(String notesJson) { this.notesJson = notesJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
