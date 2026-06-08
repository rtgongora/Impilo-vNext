package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "adverse_transfusion_reactions", schema = "madi")
public class AdverseTransfusionReactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reaction_id", nullable = false, unique = true)
    private UUID reactionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "episode_id")
    private UUID episodeId;

@Column(name = "reaction_type", nullable = false)
    private String reactionType;

@Column(name = "severity")
    private String severity;

@Column(name = "status")
    private String status;

@Column(name = "description", columnDefinition = "TEXT")
    private String description;

@Column(name = "reported_by")
    private String reportedBy;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "reported_at")
    private OffsetDateTime reportedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (reactionId == null) {
            reactionId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getReactionId() { return reactionId; }
    public void setReactionId(UUID reactionId) { this.reactionId = reactionId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }

    public String getReactionType() { return reactionType; }
    public void setReactionType(String reactionType) { this.reactionType = reactionType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getReportedBy() { return reportedBy; }
    public void setReportedBy(String reportedBy) { this.reportedBy = reportedBy; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getReportedAt() { return reportedAt; }
    public void setReportedAt(OffsetDateTime reportedAt) { this.reportedAt = reportedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}