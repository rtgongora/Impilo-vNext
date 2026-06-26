package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rit_case_message", schema = "rito")
public class CaseMessageEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    @Column(name = "channel", length = 48)
    private String channel;

    @Column(name = "author_actor_id", length = 128)
    private String authorActorId;

    @Column(name = "author_actor_type", length = 48)
    private String authorActorType;

    @Column(name = "visibility", nullable = false, length = 24)
    private String visibility;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getAuthorActorId() { return authorActorId; }
    public void setAuthorActorId(String authorActorId) { this.authorActorId = authorActorId; }
    public String getAuthorActorType() { return authorActorType; }
    public void setAuthorActorType(String authorActorType) { this.authorActorType = authorActorType; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
