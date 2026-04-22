package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.msikaflow.domain.ProofType;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mf_proofs")
public class ProofEntity {

    @Id
    @Column(name = "proof_id", nullable = false, length = 26)
    private String proofId;

    @Column(name = "plan_id", length = 26)
    private String planId;

    @Column(name = "handoff_id", length = 26)
    private String handoffId;

    @Enumerated(EnumType.STRING)
    @Column(name = "proof_type", nullable = false, length = 40)
    private ProofType proofType;

    @Column(name = "proof_reference", nullable = false, length = 255)
    private String proofReference;

    @Column(name = "verification_outcome", nullable = false, length = 40)
    private String verificationOutcome = "UNVERIFIED";

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (metadataJson == null) metadataJson = "{}";
    }

    public String getProofId() { return proofId; }
    public void setProofId(String proofId) { this.proofId = proofId; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getHandoffId() { return handoffId; }
    public void setHandoffId(String handoffId) { this.handoffId = handoffId; }
    public ProofType getProofType() { return proofType; }
    public void setProofType(ProofType proofType) { this.proofType = proofType; }
    public String getProofReference() { return proofReference; }
    public void setProofReference(String proofReference) { this.proofReference = proofReference; }
    public String getVerificationOutcome() { return verificationOutcome; }
    public void setVerificationOutcome(String verificationOutcome) { this.verificationOutcome = verificationOutcome; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(OffsetDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

