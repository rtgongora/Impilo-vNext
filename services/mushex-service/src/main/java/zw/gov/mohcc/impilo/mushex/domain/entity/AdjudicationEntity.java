package zw.gov.mohcc.impilo.mushex.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "mushex_adjudications")
public class AdjudicationEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "claim_id", nullable = false)
    private String claimId;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "decision", columnDefinition = "jsonb")
    private String decision;

    @Column(name = "patient_residual", nullable = false, precision = 14, scale = 2)
    private BigDecimal patientResidual;

    @Column(name = "insurer_payable", nullable = false, precision = 14, scale = 2)
    private BigDecimal insurerPayable;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClaimId() {
        return claimId;
    }

    public void setClaimId(String claimId) {
        this.claimId = claimId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public BigDecimal getPatientResidual() {
        return patientResidual;
    }

    public void setPatientResidual(BigDecimal patientResidual) {
        this.patientResidual = patientResidual;
    }

    public BigDecimal getInsurerPayable() {
        return insurerPayable;
    }

    public void setInsurerPayable(BigDecimal insurerPayable) {
        this.insurerPayable = insurerPayable;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
