package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_delivery_proofs")
public class DeliveryProofEntity {

    @Id
    @Column(name = "proof_id", nullable = false)
    private UUID proofId;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "proof_stage", nullable = false, length = 32)
    private String proofStage = "DELIVERY";

    @Column(name = "proof_method", nullable = false, length = 32)
    private String proofMethod;

    @Column(name = "captured_by", length = 255)
    private String capturedBy;

    @Column(name = "captured_at", nullable = false)
    private OffsetDateTime capturedAt = OffsetDateTime.now();

    @Column(name = "otp_code", length = 16)
    private String otpCode;

    @Column(name = "signature_uri", length = 1024)
    private String signatureUri;

    @Column(name = "photo_uri", length = 1024)
    private String photoUri;

    @Column(name = "biometric_ref", length = 255)
    private String biometricRef;

    @Column(name = "geofence_match")
    private Boolean geofenceMatch;

    @Column(name = "locker_code", length = 64)
    private String lockerCode;

    @Column(name = "webhook_ref", length = 255)
    private String webhookRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "verified", nullable = false)
    private boolean verified;

    public DeliveryProofEntity() {}

    public UUID getProofId() { return proofId; }
    public void setProofId(UUID v) { this.proofId = v; }
    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID v) { this.deliveryId = v; }
    public String getProofStage() { return proofStage; }
    public void setProofStage(String v) { this.proofStage = v; }
    public String getProofMethod() { return proofMethod; }
    public void setProofMethod(String v) { this.proofMethod = v; }
    public String getCapturedBy() { return capturedBy; }
    public void setCapturedBy(String v) { this.capturedBy = v; }
    public OffsetDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(OffsetDateTime v) { this.capturedAt = v; }
    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String v) { this.otpCode = v; }
    public String getSignatureUri() { return signatureUri; }
    public void setSignatureUri(String v) { this.signatureUri = v; }
    public String getPhotoUri() { return photoUri; }
    public void setPhotoUri(String v) { this.photoUri = v; }
    public String getBiometricRef() { return biometricRef; }
    public void setBiometricRef(String v) { this.biometricRef = v; }
    public Boolean getGeofenceMatch() { return geofenceMatch; }
    public void setGeofenceMatch(Boolean v) { this.geofenceMatch = v; }
    public String getLockerCode() { return lockerCode; }
    public void setLockerCode(String v) { this.lockerCode = v; }
    public String getWebhookRef() { return webhookRef; }
    public void setWebhookRef(String v) { this.webhookRef = v; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String v) { this.metadataJson = v; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean v) { this.verified = v; }
}
