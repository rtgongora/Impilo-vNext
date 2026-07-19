package zw.gov.mohcc.impilo.abis.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import zw.gov.mohcc.impilo.abis.core.BiometricModality;

import java.time.Instant;
import java.util.UUID;

/**
 * Encrypted biometric template row. {@code subjectRef} is an opaque ABIS subject reference —
 * NEVER a Health ID; TSHEPO holds the biometric-subject → HID mapping. ABIS stores no
 * demographics and no PII.
 */
@Entity
@Table(name = "template", schema = "abis")
public class TemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_ref", nullable = false, length = 128)
    private String subjectRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false, length = 32)
    private BiometricModality modality;

    @Column(name = "position", nullable = false, length = 32)
    private String position = "PRIMARY";

    @Column(name = "quality_score")
    private Integer qualityScore;

    @Column(name = "algorithm_version", length = 64)
    private String algorithmVersion;

    @Column(name = "template_encrypted", nullable = false)
    private byte[] templateEncrypted;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }

    public BiometricModality getModality() { return modality; }
    public void setModality(BiometricModality modality) { this.modality = modality; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public Integer getQualityScore() { return qualityScore; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }

    public String getAlgorithmVersion() { return algorithmVersion; }
    public void setAlgorithmVersion(String algorithmVersion) { this.algorithmVersion = algorithmVersion; }

    public byte[] getTemplateEncrypted() { return templateEncrypted; }
    public void setTemplateEncrypted(byte[] templateEncrypted) { this.templateEncrypted = templateEncrypted; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
