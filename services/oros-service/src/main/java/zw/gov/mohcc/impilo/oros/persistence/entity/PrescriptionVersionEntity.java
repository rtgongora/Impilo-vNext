package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable prescription version (OF-B2, Vol II §9.2). Signed content is immutable
 * by construction: any change after signing is a new version superseding — never
 * editing — its predecessor. The detached JWS (via tshepo-keys) binds to
 * {@code contentHash} over the canonical serialisation.
 */
@Entity
@Table(name = "oros_prescription_versions")
public class PrescriptionVersionEntity {

    @Id
    @Column(name = "prescription_version_id", nullable = false, length = 26)
    private String prescriptionVersionId;

    @Column(name = "prescription_id", nullable = false, length = 26)
    private String prescriptionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "supersedes_version_id", length = 26)
    private String supersedesVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", columnDefinition = "jsonb", nullable = false)
    private String contentJson;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "signature_jws", columnDefinition = "TEXT")
    private String signatureJws;

    @Column(name = "signature_key_id", length = 128)
    private String signatureKeyId;

    @Column(name = "signed_by", length = 128)
    private String signedBy;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public String getPrescriptionVersionId() { return prescriptionVersionId; }
    public void setPrescriptionVersionId(String v) { this.prescriptionVersionId = v; }
    public String getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(String prescriptionId) { this.prescriptionId = prescriptionId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getSupersedesVersionId() { return supersedesVersionId; }
    public void setSupersedesVersionId(String supersedesVersionId) { this.supersedesVersionId = supersedesVersionId; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getSignatureJws() { return signatureJws; }
    public void setSignatureJws(String signatureJws) { this.signatureJws = signatureJws; }
    public String getSignatureKeyId() { return signatureKeyId; }
    public void setSignatureKeyId(String signatureKeyId) { this.signatureKeyId = signatureKeyId; }
    public String getSignedBy() { return signedBy; }
    public void setSignedBy(String signedBy) { this.signedBy = signedBy; }
    public OffsetDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(OffsetDateTime signedAt) { this.signedAt = signedAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isSigned() { return signatureJws != null && !signatureJws.isBlank(); }
}
