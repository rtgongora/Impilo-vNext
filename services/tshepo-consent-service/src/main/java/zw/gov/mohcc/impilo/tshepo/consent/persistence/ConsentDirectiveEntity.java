package zw.gov.mohcc.impilo.tshepo.consent.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code tshepo_consent.consent_directive} table.
 *
 * <p>Stores patient consent directives with the full FHIR R4 Consent resource
 * serialized as JSONB. Contains only opaque references (CPID/HealthID) — no PII.</p>
 */
@Entity
@Table(name = "consent_directive", schema = "tshepo_consent")
public class ConsentDirectiveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_ref", nullable = false)
    private String patientRef;

    @Column(name = "grantor_ref", nullable = false)
    private String grantorRef;

    @Column(name = "grantee_ref")
    private String granteeRef;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "scope", nullable = false, length = 64)
    private String scope;

    @Column(name = "purpose", nullable = false, length = 64)
    private String purpose;

    @Column(name = "provision", nullable = false, length = 8)
    private String provision;

    @Column(name = "fhir_consent_json", nullable = false, columnDefinition = "jsonb")
    private String fhirConsentJson;

    @Column(name = "period_start")
    private Instant periodStart;

    @Column(name = "period_end")
    private Instant periodEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", columnDefinition = "text")
    private String revocationReason;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "ACTIVE";
        }
        if (provision == null) {
            provision = "permit";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // --- Getters and Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getPatientRef() {
        return patientRef;
    }

    public void setPatientRef(String patientRef) {
        this.patientRef = patientRef;
    }

    public String getGrantorRef() {
        return grantorRef;
    }

    public void setGrantorRef(String grantorRef) {
        this.grantorRef = grantorRef;
    }

    public String getGranteeRef() {
        return granteeRef;
    }

    public void setGranteeRef(String granteeRef) {
        this.granteeRef = granteeRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getProvision() {
        return provision;
    }

    public void setProvision(String provision) {
        this.provision = provision;
    }

    public String getFhirConsentJson() {
        return fhirConsentJson;
    }

    public void setFhirConsentJson(String fhirConsentJson) {
        this.fhirConsentJson = fhirConsentJson;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(Instant periodStart) {
        this.periodStart = periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(Instant periodEnd) {
        this.periodEnd = periodEnd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public void setRevocationReason(String revocationReason) {
        this.revocationReason = revocationReason;
    }
}
