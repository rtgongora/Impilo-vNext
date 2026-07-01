package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Native Impilo Fundo course — the main learning unit (Phase 5A).
 *
 * <p>Distinct from the legacy {@link LearningResourceEntity}, which models a
 * generic "resource" (FUNDO/MOODLE deep-launch link). Fundo's native LMS
 * surface uses {@link CourseEntity} as the catalog primitive and does not
 * require any external LMS.</p>
 */
@Entity
@Table(
        name = "lrn_course",
        uniqueConstraints = @UniqueConstraint(name = "uq_lrn_course_code", columnNames = {"tenant_id", "code"}))
public class CourseEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 160)
    private String code;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 128)
    private String category;

    @Column(name = "level", length = 64)
    private String level;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "language", nullable = false, length = 16)
    private String language = "en";

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "cpd_eligible", nullable = false)
    private boolean cpdEligible;

    @Column(name = "cpd_points")
    private Integer cpdPoints;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Integer getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
    public void setEstimatedDurationMinutes(Integer m) { this.estimatedDurationMinutes = m; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
    public boolean isCpdEligible() { return cpdEligible; }
    public void setCpdEligible(boolean cpdEligible) { this.cpdEligible = cpdEligible; }
    public Integer getCpdPoints() { return cpdPoints; }
    public void setCpdPoints(Integer cpdPoints) { this.cpdPoints = cpdPoints; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
