package zw.gov.mohcc.impilo.surgery.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The surgical decision-making record (S3, surgical domain pack §8) — one row per
 * {@code surgical_episode}, refined over time until a final decision is recorded. See V004's own
 * header for why diagnosis/certainty are deliberately NOT repeated here (read
 * {@code surgical_episode.pct_problem_ref} instead) and why {@code material_risks_considered} is
 * a distinct fact from mvumo's consent-conversation {@code material_risks_explained}.
 */
@Entity
@Table(name = "surgical_decision", schema = "surgery")
public class SurgicalDecisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "surgical_episode_id", nullable = false)
    private UUID surgicalEpisodeId;

    @Column(name = "natural_history", columnDefinition = "text")
    private String naturalHistory;

    @Column(name = "expected_benefit", columnDefinition = "text")
    private String expectedBenefit;

    @Column(name = "material_risks_considered", columnDefinition = "text")
    private String materialRisksConsidered;

    @Column(name = "anaesthetic_implications", columnDefinition = "text")
    private String anaestheticImplications;

    @Column(name = "blood_implications", columnDefinition = "text")
    private String bloodImplications;

    @Column(name = "functional_implications", columnDefinition = "text")
    private String functionalImplications;

    @Column(name = "fertility_implications", columnDefinition = "text")
    private String fertilityImplications;

    @Column(name = "stoma_possibility")
    private Boolean stomaPossibility;

    @Column(name = "stoma_possibility_notes", columnDefinition = "text")
    private String stomaPossibilityNotes;

    @Column(name = "implant_possibility")
    private Boolean implantPossibility;

    @Column(name = "implant_possibility_notes", columnDefinition = "text")
    private String implantPossibilityNotes;

    @Column(name = "rehabilitation_expectation", columnDefinition = "text")
    private String rehabilitationExpectation;

    @Column(name = "financial_access_implications", columnDefinition = "text")
    private String financialAccessImplications;

    @Column(name = "patient_preference", columnDefinition = "text")
    private String patientPreference;

    @Column(name = "final_decision", length = 24)
    private String finalDecision;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getSurgicalEpisodeId() { return surgicalEpisodeId; }
    public void setSurgicalEpisodeId(UUID v) { this.surgicalEpisodeId = v; }
    public String getNaturalHistory() { return naturalHistory; }
    public void setNaturalHistory(String v) { this.naturalHistory = v; }
    public String getExpectedBenefit() { return expectedBenefit; }
    public void setExpectedBenefit(String v) { this.expectedBenefit = v; }
    public String getMaterialRisksConsidered() { return materialRisksConsidered; }
    public void setMaterialRisksConsidered(String v) { this.materialRisksConsidered = v; }
    public String getAnaestheticImplications() { return anaestheticImplications; }
    public void setAnaestheticImplications(String v) { this.anaestheticImplications = v; }
    public String getBloodImplications() { return bloodImplications; }
    public void setBloodImplications(String v) { this.bloodImplications = v; }
    public String getFunctionalImplications() { return functionalImplications; }
    public void setFunctionalImplications(String v) { this.functionalImplications = v; }
    public String getFertilityImplications() { return fertilityImplications; }
    public void setFertilityImplications(String v) { this.fertilityImplications = v; }
    public Boolean getStomaPossibility() { return stomaPossibility; }
    public void setStomaPossibility(Boolean v) { this.stomaPossibility = v; }
    public String getStomaPossibilityNotes() { return stomaPossibilityNotes; }
    public void setStomaPossibilityNotes(String v) { this.stomaPossibilityNotes = v; }
    public Boolean getImplantPossibility() { return implantPossibility; }
    public void setImplantPossibility(Boolean v) { this.implantPossibility = v; }
    public String getImplantPossibilityNotes() { return implantPossibilityNotes; }
    public void setImplantPossibilityNotes(String v) { this.implantPossibilityNotes = v; }
    public String getRehabilitationExpectation() { return rehabilitationExpectation; }
    public void setRehabilitationExpectation(String v) { this.rehabilitationExpectation = v; }
    public String getFinancialAccessImplications() { return financialAccessImplications; }
    public void setFinancialAccessImplications(String v) { this.financialAccessImplications = v; }
    public String getPatientPreference() { return patientPreference; }
    public void setPatientPreference(String v) { this.patientPreference = v; }
    public String getFinalDecision() { return finalDecision; }
    public void setFinalDecision(String v) { this.finalDecision = v; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String v) { this.decidedBy = v; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(OffsetDateTime v) { this.decidedAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
