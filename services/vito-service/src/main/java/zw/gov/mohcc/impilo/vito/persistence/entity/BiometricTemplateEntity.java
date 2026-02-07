package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.vito.core.BiometricModality;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "biometric_template", schema = "vito")
public class BiometricTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "health_id", nullable = false)
    private UUID healthId;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false)
    private BiometricModality modality;

    @Column(name = "position")
    private String position;

    @Column(name = "template_hash", nullable = false)
    private String templateHash;

    @Column(name = "template_data", nullable = false)
    private byte[] templateData;

    @Column(name = "quality_score")
    private Integer qualityScore;

    @Column(name = "capture_device")
    private String captureDevice;

    @Column(name = "captured_by", nullable = false)
    private String capturedBy;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "captured_at", nullable = false)
    private OffsetDateTime capturedAt;

    @Column(name = "superseded_at")
    private OffsetDateTime supersededAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        capturedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getHealthId() { return healthId; }
    public void setHealthId(UUID healthId) { this.healthId = healthId; }
    public BiometricModality getModality() { return modality; }
    public void setModality(BiometricModality modality) { this.modality = modality; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getTemplateHash() { return templateHash; }
    public void setTemplateHash(String templateHash) { this.templateHash = templateHash; }
    public byte[] getTemplateData() { return templateData; }
    public void setTemplateData(byte[] templateData) { this.templateData = templateData; }
    public Integer getQualityScore() { return qualityScore; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }
    public String getCaptureDevice() { return captureDevice; }
    public void setCaptureDevice(String captureDevice) { this.captureDevice = captureDevice; }
    public String getCapturedBy() { return capturedBy; }
    public void setCapturedBy(String capturedBy) { this.capturedBy = capturedBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCapturedAt() { return capturedAt; }
    public OffsetDateTime getSupersededAt() { return supersededAt; }
    public void setSupersededAt(OffsetDateTime supersededAt) { this.supersededAt = supersededAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
