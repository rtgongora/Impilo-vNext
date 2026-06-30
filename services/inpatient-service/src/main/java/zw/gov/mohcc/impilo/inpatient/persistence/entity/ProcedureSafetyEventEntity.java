package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Theatre safety event routed to its owner (Rito/Madi/asset-registry/PCT-death). Inpatient records the fact + owner ref. */
@Entity
@Table(name = "procedure_safety_event", schema = "inpatient")
public class ProcedureSafetyEventEntity {

    @Id
    @Column(name = "safety_event_id")
    private UUID safetyEventId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "severity")
    private String severity;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "routed_owner", nullable = false)
    private String routedOwner;

    @Column(name = "owner_ref")
    private String ownerRef;

    @Column(name = "routed_status", nullable = false)
    private String routedStatus = "ROUTED";

    @Column(name = "reported_by")
    private String reportedBy;

    @Column(name = "reported_at", nullable = false)
    private OffsetDateTime reportedAt;

    @PrePersist
    void prePersist() {
        if (safetyEventId == null) safetyEventId = UUID.randomUUID();
        if (reportedAt == null) reportedAt = OffsetDateTime.now();
    }

    public UUID getSafetyEventId() { return safetyEventId; }
    public void setSafetyEventId(UUID v) { this.safetyEventId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getRoutedOwner() { return routedOwner; }
    public void setRoutedOwner(String v) { this.routedOwner = v; }
    public String getOwnerRef() { return ownerRef; }
    public void setOwnerRef(String v) { this.ownerRef = v; }
    public String getRoutedStatus() { return routedStatus; }
    public void setRoutedStatus(String v) { this.routedStatus = v; }
    public String getReportedBy() { return reportedBy; }
    public void setReportedBy(String v) { this.reportedBy = v; }
    public OffsetDateTime getReportedAt() { return reportedAt; }
    public void setReportedAt(OffsetDateTime v) { this.reportedAt = v; }
}
