package zw.gov.mohcc.impilo.pacs.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to {@code pacs.imaging_study_exception} — the reconciliation exceptions
 * queue. A study that cannot be safely attached to a patient/order raises an exception row;
 * resolution records who acted, when, the action taken, and a note.
 */
@Entity
@Table(name = "imaging_study_exception", schema = "pacs")
public class ImagingStudyExceptionEntity {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_DISMISSED = "DISMISSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "study_id")
    private Long studyId;

    @Column(name = "exception_type", nullable = false, length = 64)
    private String exceptionType;

    @Column(name = "status", nullable = false, length = 32)
    private String status = STATUS_OPEN;

    @Column(name = "detail")
    private String detail;

    @Column(name = "raised_by", length = 128)
    private String raisedBy;

    @Column(name = "raised_at", nullable = false)
    private OffsetDateTime raisedAt;

    @Column(name = "resolved_by", length = 128)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolution_action", length = 64)
    private String resolutionAction;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (raisedAt == null) {
            raisedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public Long getStudyId() { return studyId; }
    public void setStudyId(Long studyId) { this.studyId = studyId; }

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getRaisedBy() { return raisedBy; }
    public void setRaisedBy(String raisedBy) { this.raisedBy = raisedBy; }

    public OffsetDateTime getRaisedAt() { return raisedAt; }
    public void setRaisedAt(OffsetDateTime raisedAt) { this.raisedAt = raisedAt; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolutionAction() { return resolutionAction; }
    public void setResolutionAction(String resolutionAction) { this.resolutionAction = resolutionAction; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
