package zw.gov.mohcc.impilo.guidance.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An advisory interaction event (V014) — a VIEW, DISMISS or FEEDBACK impression against a
 * {@link ServiceAdvisoryEntity}. {@code anonDismissalKey} is an opaque, caller-supplied key
 * (never PII) so the public lane can record a dismissal without identifying a person.
 */
@Entity
@Table(name = "advisory_impression", schema = "guidance")
public class AdvisoryImpressionEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "advisory_id", nullable = false) private UUID advisoryId;
    @Column(name = "tenant_id", nullable = false) private String tenantId = "national-spine";
    /** VIEW | DISMISS | FEEDBACK */
    @Column(nullable = false, length = 16) private String event;
    @Column(name = "anon_dismissal_key", length = 255) private String anonDismissalKey;
    @Column(name = "occurred_at", nullable = false) private OffsetDateTime occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) occurredAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getAdvisoryId() { return advisoryId; }
    public void setAdvisoryId(UUID advisoryId) { this.advisoryId = advisoryId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getAnonDismissalKey() { return anonDismissalKey; }
    public void setAnonDismissalKey(String anonDismissalKey) { this.anonDismissalKey = anonDismissalKey; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
}
