package zw.gov.mohcc.impilo.clinical.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Last-known open/retired state of a multimorbidity finding for one subject (V071).
 *
 * <p>Exists so a subsequent assess can emit {@code MULTIMORBIDITY_ISSUE_RESOLVED} when a code that
 * was OPEN is answered as no longer DETECTED. Without this row the only alternative is to treat
 * silence as clearance — the failure the pack is written against.</p>
 */
@Entity
@Table(schema = "clinical", name = "multimorbidity_detection_snapshot")
public class MultimorbidityDetectionSnapshotEntity {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_RETIRED = "RETIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "subject_cpid", nullable = false, length = 64)
    private String subjectCpid;

    @Column(nullable = false, length = 128)
    private String code;

    @Column(name = "detection_key", nullable = false, length = 128)
    private String detectionKey;

    @Column(length = 32)
    private String severity;

    @Column(name = "content_version", length = 64)
    private String contentVersion;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSubjectCpid() {
        return subjectCpid;
    }

    public void setSubjectCpid(String subjectCpid) {
        this.subjectCpid = subjectCpid;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDetectionKey() {
        return detectionKey;
    }

    public void setDetectionKey(String detectionKey) {
        this.detectionKey = detectionKey;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public void setContentVersion(String contentVersion) {
        this.contentVersion = contentVersion;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
