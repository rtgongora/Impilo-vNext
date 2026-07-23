package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * OF-B8 — one payer-formulary entry on a plan version (V020): is a medication
 * (coded against the ZIBO national registry, WHO ATC) covered, at which
 * tier/copay class, priced under WHICH plan benefit ({@code benefitCode} — the
 * benefit-code mapping), and does the payer require prior authorisation.
 * The middle layer of the three-layer formulary stance.
 */
@Entity
@Table(name = "cv_formulary")
public class FormularyEntity {

    /** Default coding system for medication codes — the ZIBO-registered WHO ATC system. */
    public static final String ATC_CODING_SYSTEM = "http://www.whocc.no/atc";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "plan_version_id", nullable = false)
    private UUID planVersionId;

    @Column(name = "medication_code", nullable = false, length = 64)
    private String medicationCode;

    @Column(name = "coding_system", nullable = false, length = 255)
    private String codingSystem = ATC_CODING_SYSTEM;

    @Column(name = "benefit_code", nullable = false, length = 64)
    private String benefitCode;

    @Column(name = "covered", nullable = false)
    private boolean covered = true;

    @Column(name = "tier", nullable = false, length = 24)
    private String tier = "STANDARD";

    @Column(name = "copay_class", length = 24)
    private String copayClass;

    @Column(name = "requires_authorisation", nullable = false)
    private boolean requiresAuthorisation = false;

    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom = OffsetDateTime.now();

    @Column(name = "effective_to")
    private OffsetDateTime effectiveTo;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public FormularyEntity() {}

    public static FormularyEntity create(UUID tenantId, String podId, UUID planVersionId,
                                         String medicationCode, String codingSystem, String benefitCode) {
        FormularyEntity f = new FormularyEntity();
        f.id = UUID.randomUUID();
        f.tenantId = tenantId;
        f.podId = podId != null ? podId : "national-spine";
        f.planVersionId = planVersionId;
        f.medicationCode = medicationCode;
        f.codingSystem = codingSystem != null && !codingSystem.isBlank() ? codingSystem : ATC_CODING_SYSTEM;
        f.benefitCode = benefitCode;
        return f;
    }

    /** True when the entry's effective window contains {@code at}. */
    public boolean activeAt(OffsetDateTime at) {
        return !effectiveFrom.isAfter(at) && (effectiveTo == null || effectiveTo.isAfter(at));
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public UUID getPlanVersionId() { return planVersionId; }
    public String getMedicationCode() { return medicationCode; }
    public String getCodingSystem() { return codingSystem; }
    public String getBenefitCode() { return benefitCode; }
    public void setBenefitCode(String v) { this.benefitCode = v; }
    public boolean isCovered() { return covered; }
    public void setCovered(boolean v) { this.covered = v; }
    public String getTier() { return tier; }
    public void setTier(String v) { this.tier = v; }
    public String getCopayClass() { return copayClass; }
    public void setCopayClass(String v) { this.copayClass = v; }
    public boolean isRequiresAuthorisation() { return requiresAuthorisation; }
    public void setRequiresAuthorisation(boolean v) { this.requiresAuthorisation = v; }
    public OffsetDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(OffsetDateTime v) { this.effectiveFrom = v; }
    public OffsetDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(OffsetDateTime v) { this.effectiveTo = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = OffsetDateTime.now(); }
}
