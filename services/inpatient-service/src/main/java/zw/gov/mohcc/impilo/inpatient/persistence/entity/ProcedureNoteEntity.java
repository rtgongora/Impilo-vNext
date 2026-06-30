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
