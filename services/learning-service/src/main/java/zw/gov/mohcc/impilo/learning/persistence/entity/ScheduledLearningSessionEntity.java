package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA mapping for {@code lrn_scheduled_learning_session} (V008). Columns mirror V008
 * exactly. The legacy free-text {@code facilitator} / {@code location_ref} columns are
 * retained; later waves add nullable {@code facilitator_id} / {@code venue_id} FKs
 * alongside them (dual-write, read prefers the FK).
 */
@Entity
@Table(name = "lrn_scheduled_learning_session")
public class ScheduledLearningSessionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "cohort_id")
    private UUID cohortId;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "session_type", nullable = false, length = 64)
    private String sessionType;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    /** Deprecated free-text; superseded by {@code facilitator_id} in a later wave. */
    @Column(name = "facilitator", length = 255)
    private String facilitator;

    /** Deprecated free-text; superseded by {@code venue_id} in a later wave. */
    @Column(name = "location_ref", length = 1024)
    private String locationRef;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }
    public UUID getCohortId() { return cohortId; }
    public void setCohortId(UUID cohortId) { this.cohortId = cohortId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSessionType() { return sessionType; }
    public void setSessionType(String sessionType) { this.sessionType = sessionType; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(OffsetDateTime startsAt) { this.startsAt = startsAt; }
    public OffsetDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(OffsetDateTime endsAt) { this.endsAt = endsAt; }
    public String getFacilitator() { return facilitator; }
    public void setFacilitator(String facilitator) { this.facilitator = facilitator; }
    public String getLocationRef() { return locationRef; }
    public void setLocationRef(String locationRef) { this.locationRef = locationRef; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
