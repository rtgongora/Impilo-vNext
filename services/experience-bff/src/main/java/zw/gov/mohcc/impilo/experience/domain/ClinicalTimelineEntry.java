package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "clinical_timeline")
public class ClinicalTimelineEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "event_type")
    private String eventType;

    private String title;

    private String description;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ClinicalTimelineEntry() {}

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getEncounterId() { return encounterId; }
    public String getEventType() { return eventType; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public String getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
