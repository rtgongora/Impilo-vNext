package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Signable operative / procedure note for a theatre episode (DRAFT → SIGNED), Butano-linked on sign. */
@Entity
@Table(name = "procedure_note", schema = "inpatient")
public class ProcedureNoteEntity {

    @Id
    @Column(name = "note_id")
    private UUID noteId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @Column(name = "performed_procedure", columnDefinition = "TEXT")
    private String performedProcedure;

    @Column(name = "findings", columnDefinition = "TEXT")
    private String findings;

    @Column(name = "specimens", columnDefinition = "TEXT")
    private String specimens;

    @Column(name = "implants", columnDefinition = "TEXT")
    private String implants;

    @Column(name = "estimated_blood_loss_ml")
    private Integer estimatedBloodLossMl;

    @Column(name = "complications", columnDefinition = "TEXT")
    private String complications;

    @Column(name = "counts_correct")
    private Boolean countsCorrect;

    @Column(name = "postop_plan", columnDefinition = "TEXT")
    private String postopPlan;

    // ── SB-5 (audit §13) — the eleven operative-record fields the audit named as absent.
    //    Named to stay distinct from their near-namesakes elsewhere in the schema: the
    //    anaesthetic technique on procedure_anaesthesia_chart_entry, and the fluids and
    //    drains channels on procedure_pacu_observation. See V304's header. ──
    @Column(name = "patient_position", columnDefinition = "TEXT")
    private String patientPosition;

    @Column(name = "skin_preparation", columnDefinition = "TEXT")
    private String skinPreparation;

    @Column(name = "incision", columnDefinition = "TEXT")
    private String incision;

    @Column(name = "operative_steps", columnDefinition = "TEXT")
    private String operativeSteps;

    @Column(name = "operative_technique", columnDefinition = "TEXT")
    private String operativeTechnique;

    @Column(name = "intraoperative_fluids", columnDefinition = "TEXT")
    private String intraoperativeFluids;

    @Column(name = "drains_placed", columnDefinition = "TEXT")
    private String drainsPlaced;

    @Column(name = "stomas_formed", columnDefinition = "TEXT")
    private String stomasFormed;

    @Column(name = "closure_method", columnDefinition = "TEXT")
    private String closureMethod;

    /** CLEAN | CLEAN_CONTAMINATED | CONTAMINATED | DIRTY — the template's vocabulary. */
    @Column(name = "wound_classification", length = 24)
    private String woundClassification;

    @Column(name = "postoperative_instructions", columnDefinition = "TEXT")
    private String postoperativeInstructions;

    /** surgery.surgical_operative_template.id — a reference across services, never an FK. */
    @Column(name = "operative_template_ref")
    private UUID operativeTemplateRef;

    @Column(name = "operative_template_code", length = 32)
    private String operativeTemplateCode;

    @Column(name = "note_json", columnDefinition = "TEXT")
    private String noteJson;

    @Column(name = "signed_by")
    private String signedBy;

    @Column(name = "signed_provider_id")
    private String signedProviderId;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    @Column(name = "butano_procedure_ref")
    private String butanoProcedureRef;

    @Column(name = "butano_document_ref")
    private String butanoDocumentRef;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (noteId == null) noteId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getNoteId() { return noteId; }
    public void setNoteId(UUID v) { this.noteId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getPerformedProcedure() { return performedProcedure; }
    public void setPerformedProcedure(String v) { this.performedProcedure = v; }
    public String getFindings() { return findings; }
    public void setFindings(String v) { this.findings = v; }
    public String getSpecimens() { return specimens; }
    public void setSpecimens(String v) { this.specimens = v; }
    public String getImplants() { return implants; }
    public void setImplants(String v) { this.implants = v; }
    public Integer getEstimatedBloodLossMl() { return estimatedBloodLossMl; }
    public void setEstimatedBloodLossMl(Integer v) { this.estimatedBloodLossMl = v; }
    public String getComplications() { return complications; }
    public void setComplications(String v) { this.complications = v; }
    public Boolean getCountsCorrect() { return countsCorrect; }
    public void setCountsCorrect(Boolean v) { this.countsCorrect = v; }
    public String getPostopPlan() { return postopPlan; }
    public void setPostopPlan(String v) { this.postopPlan = v; }
    public String getPatientPosition() { return patientPosition; }
    public void setPatientPosition(String v) { this.patientPosition = v; }
    public String getSkinPreparation() { return skinPreparation; }
    public void setSkinPreparation(String v) { this.skinPreparation = v; }
    public String getIncision() { return incision; }
    public void setIncision(String v) { this.incision = v; }
    public String getOperativeSteps() { return operativeSteps; }
    public void setOperativeSteps(String v) { this.operativeSteps = v; }
    public String getOperativeTechnique() { return operativeTechnique; }
    public void setOperativeTechnique(String v) { this.operativeTechnique = v; }
    public String getIntraoperativeFluids() { return intraoperativeFluids; }
    public void setIntraoperativeFluids(String v) { this.intraoperativeFluids = v; }
    public String getDrainsPlaced() { return drainsPlaced; }
    public void setDrainsPlaced(String v) { this.drainsPlaced = v; }
    public String getStomasFormed() { return stomasFormed; }
    public void setStomasFormed(String v) { this.stomasFormed = v; }
    public String getClosureMethod() { return closureMethod; }
    public void setClosureMethod(String v) { this.closureMethod = v; }
    public String getWoundClassification() { return woundClassification; }
    public void setWoundClassification(String v) { this.woundClassification = v; }
    public String getPostoperativeInstructions() { return postoperativeInstructions; }
    public void setPostoperativeInstructions(String v) { this.postoperativeInstructions = v; }
    public UUID getOperativeTemplateRef() { return operativeTemplateRef; }
    public void setOperativeTemplateRef(UUID v) { this.operativeTemplateRef = v; }
    public String getOperativeTemplateCode() { return operativeTemplateCode; }
    public void setOperativeTemplateCode(String v) { this.operativeTemplateCode = v; }

    public String getNoteJson() { return noteJson; }
    public void setNoteJson(String v) { this.noteJson = v; }
    public String getSignedBy() { return signedBy; }
    public void setSignedBy(String v) { this.signedBy = v; }
    public String getSignedProviderId() { return signedProviderId; }
    public void setSignedProviderId(String v) { this.signedProviderId = v; }
    public OffsetDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(OffsetDateTime v) { this.signedAt = v; }
    public String getButanoProcedureRef() { return butanoProcedureRef; }
    public void setButanoProcedureRef(String v) { this.butanoProcedureRef = v; }
    public String getButanoDocumentRef() { return butanoDocumentRef; }
    public void setButanoDocumentRef(String v) { this.butanoDocumentRef = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
