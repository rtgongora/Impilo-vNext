package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.varapi.core.biometric.ProviderBiometricModality;
import zw.gov.mohcc.impilo.varapi.core.biometric.ProviderBiometricTemplateConverter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_biometric_template", schema = "varapi")
public class ProviderBiometricTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false, length = 20)
    private ProviderBiometricModality modality;

    @Column(name = "position", length = 64)
    private String position;

    @Column(name = "template_hash", nullable = false, length = 64)
    private String templateHash;

    // Encrypted at rest (AES-256-GCM) — biometric templates are irreversible, non-resettable secrets
    // and must never sit in plaintext (PII Enforcement Wave 0.1).
    @Convert(converter = ProviderBiometricTemplateConverter.class)
    @Column(name = "template_data", nullable = false)
    private byte[] templateData;

    @Column(name = "quality_score")
    private Integer qualityScore;

    @Column(name = "capture_device")
    private String captureDevice;

    @Column(name = "captured_by", nullable = false)
    private String capturedBy;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "vendor_engine", length = 64)
    private String vendorEngine;

    @Column(name = "liveness_supported", nullable = false)
    private boolean livenessSupported;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        capturedAt = now;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public void setProfileId(UUID profileId) {
        this.profileId = profileId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public ProviderBiometricModality getModality() {
        return modality;
    }

    public void setModality(ProviderBiometricModality modality) {
        this.modality = modality;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getTemplateHash() {
        return templateHash;
    }

    public void setTemplateHash(String templateHash) {
        this.templateHash = templateHash;
    }

    public byte[] getTemplateData() {
        return templateData;
    }

    public void setTemplateData(byte[] templateData) {
        this.templateData = templateData;
    }

    public Integer getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Integer qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getCaptureDevice() {
        return captureDevice;
    }

    public void setCaptureDevice(String captureDevice) {
        this.captureDevice = captureDevice;
    }

    public String getCapturedBy() {
        return capturedBy;
    }

    public void setCapturedBy(String capturedBy) {
        this.capturedBy = capturedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }

    public void setSupersededAt(Instant supersededAt) {
        this.supersededAt = supersededAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getVendorEngine() {
        return vendorEngine;
    }

    public void setVendorEngine(String vendorEngine) {
        this.vendorEngine = vendorEngine;
    }

    public boolean isLivenessSupported() {
        return livenessSupported;
    }

    public void setLivenessSupported(boolean livenessSupported) {
        this.livenessSupported = livenessSupported;
    }
}
