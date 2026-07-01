package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Signature / countersignature on a form response. */
@Entity
@Table(name = "pct_form_signature")
public class FormSignatureEntity {

    @Id
    @Column(name = "signature_id", nullable = false)
    private UUID signatureId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "response_id", nullable = false)
    private UUID responseId;

    @Column(name = "signature_kind", nullable = false)
    private String signatureKind;   // AUTHOR | COUNTERSIGN | SUPERVISOR

    @Column(name = "signed_by", nullable = false)
    private String signedBy;

    @Column(name = "signed_by_cadre")
    private String signedByCadre;

    @Column(name = "signed_by_role")
    private String signedByRole;

    @Column(name = "step_up_ref")
    private String stepUpRef;

    @Column(name = "attestation")
    private String attestation;

    @Column(name = "signed_at", nullable = false, updatable = false)
    private OffsetDateTime signedAt;

    @PrePersist
    protected void onCreate() {
        if (signatureId == null) {
            signatureId = UUID.randomUUID();
        }
        if (signedAt == null) {
            signedAt = OffsetDateTime.now();
        }
    }

    public UUID getSignatureId() { return signatureId; }
    public void setSignatureId(UUID signatureId) { this.signatureId = signatureId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getResponseId() { return responseId; }
    public void setResponseId(UUID responseId) { this.responseId = responseId; }

    public String getSignatureKind() { return signatureKind; }
    public void setSignatureKind(String signatureKind) { this.signatureKind = signatureKind; }

    public String getSignedBy() { return signedBy; }
    public void setSignedBy(String signedBy) { this.signedBy = signedBy; }

    public String getSignedByCadre() { return signedByCadre; }
    public void setSignedByCadre(String signedByCadre) { this.signedByCadre = signedByCadre; }

    public String getSignedByRole() { return signedByRole; }
    public void setSignedByRole(String signedByRole) { this.signedByRole = signedByRole; }

    public String getStepUpRef() { return stepUpRef; }
    public void setStepUpRef(String stepUpRef) { this.stepUpRef = stepUpRef; }

    public String getAttestation() { return attestation; }
    public void setAttestation(String attestation) { this.attestation = attestation; }

    public OffsetDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(OffsetDateTime signedAt) { this.signedAt = signedAt; }
}
