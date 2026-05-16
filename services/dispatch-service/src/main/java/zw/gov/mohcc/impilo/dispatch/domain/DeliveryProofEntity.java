package zw.gov.mohcc.impilo.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dsp_delivery_proofs")
public class DeliveryProofEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "proof_type", nullable = false)
    private String proofType;

    @Column(name = "proof_ref")
    private String proofRef;

    @Column(name = "otp_code")
    private String otpCode;

    @Column(name = "captured_by")
    private String capturedBy;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "captured_at", nullable = false)
    private OffsetDateTime capturedAt;

    protected DeliveryProofEntity() {}

    public DeliveryProofEntity(UUID id, UUID deliveryId, String proofType) {
        this.id = id;
        this.deliveryId = deliveryId;
        this.proofType = proofType;
        this.capturedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getDeliveryId() { return deliveryId; }
    public String getProofType() { return proofType; }
    public String getProofRef() { return proofRef; }
    public void setProofRef(String proofRef) { this.proofRef = proofRef; }
    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public String getCapturedBy() { return capturedBy; }
    public void setCapturedBy(String capturedBy) { this.capturedBy = capturedBy; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
    public OffsetDateTime getCapturedAt() { return capturedAt; }
}
