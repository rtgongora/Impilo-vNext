package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "procedure_postop_record", schema = "inpatient")
public class ProcedurePostopRecordEntity {

    @Id
    @Column(name = "postop_id")
    private UUID postopId;

    @Column(name = "episode_id", nullable = false, unique = true)
    private UUID episodeId;

    @Column(name = "pacu_arrival_at")
    private OffsetDateTime pacuArrivalAt;

    @Column(name = "aldrete_score")
    private Integer aldreteScore;

    @Column(name = "pain_score")
    private Integer painScore;

    @Column(name = "complications")
    private String complications;

    @Column(name = "disposition")
    private String disposition;

    @Column(name = "postop_orders_json")
    private String postopOrdersJson;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

    // ── Wave 4 §12/§13 — PACU discharge decision + escalation + inpatient continuity ──
    @Column(name = "destination")
    private String destination;

    @Column(name = "unplanned_icu", nullable = false)
    private boolean unplannedIcu = false;

    @Column(name = "return_to_theatre", nullable = false)
    private boolean returnToTheatre = false;

    @Column(name = "discharge_readiness_band")
    private String dischargeReadinessBand;

    @Column(name = "discharge_readiness_score")
    private Integer dischargeReadinessScore;

    @Column(name = "nhume_delivery_id")
    private String nhumeDeliveryId;

    @Column(name = "linked_admission_ref")
    private UUID linkedAdmissionRef;

    @Column(name = "escalated_to")
    private String escalatedTo;

    @Column(name = "escalation_ref")
    private String escalationRef;

    @Column(name = "escalated_at")
    private OffsetDateTime escalatedAt;

    @Column(name = "discharge_decided_at")
    private OffsetDateTime dischargeDecidedAt;

    @Column(name = "discharge_decided_by")
    private String dischargeDecidedBy;

    @PrePersist
    void onCreate() {
        if (postopId == null) postopId = UUID.randomUUID();
        if (recordedAt == null) recordedAt = OffsetDateTime.now();
    }

    public UUID getPostopId() { return postopId; }
    public void setPostopId(UUID postopId) { this.postopId = postopId; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }
    public OffsetDateTime getPacuArrivalAt() { return pacuArrivalAt; }
    public void setPacuArrivalAt(OffsetDateTime pacuArrivalAt) { this.pacuArrivalAt = pacuArrivalAt; }
    public Integer getAldreteScore() { return aldreteScore; }
    public void setAldreteScore(Integer aldreteScore) { this.aldreteScore = aldreteScore; }
    public Integer getPainScore() { return painScore; }
    public void setPainScore(Integer painScore) { this.painScore = painScore; }
    public String getComplications() { return complications; }
    public void setComplications(String complications) { this.complications = complications; }
    public String getDisposition() { return disposition; }
    public void setDisposition(String disposition) { this.disposition = disposition; }
    public String getPostopOrdersJson() { return postopOrdersJson; }
    public void setPostopOrdersJson(String postopOrdersJson) { this.postopOrdersJson = postopOrdersJson; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public boolean isUnplannedIcu() { return unplannedIcu; }
    public void setUnplannedIcu(boolean unplannedIcu) { this.unplannedIcu = unplannedIcu; }
    public boolean isReturnToTheatre() { return returnToTheatre; }
    public void setReturnToTheatre(boolean returnToTheatre) { this.returnToTheatre = returnToTheatre; }
    public String getDischargeReadinessBand() { return dischargeReadinessBand; }
    public void setDischargeReadinessBand(String dischargeReadinessBand) { this.dischargeReadinessBand = dischargeReadinessBand; }
    public Integer getDischargeReadinessScore() { return dischargeReadinessScore; }
    public void setDischargeReadinessScore(Integer dischargeReadinessScore) { this.dischargeReadinessScore = dischargeReadinessScore; }
    public String getNhumeDeliveryId() { return nhumeDeliveryId; }
    public void setNhumeDeliveryId(String nhumeDeliveryId) { this.nhumeDeliveryId = nhumeDeliveryId; }
    public UUID getLinkedAdmissionRef() { return linkedAdmissionRef; }
    public void setLinkedAdmissionRef(UUID linkedAdmissionRef) { this.linkedAdmissionRef = linkedAdmissionRef; }
    public String getEscalatedTo() { return escalatedTo; }
    public void setEscalatedTo(String escalatedTo) { this.escalatedTo = escalatedTo; }
    public String getEscalationRef() { return escalationRef; }
    public void setEscalationRef(String escalationRef) { this.escalationRef = escalationRef; }
    public OffsetDateTime getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(OffsetDateTime escalatedAt) { this.escalatedAt = escalatedAt; }
    public OffsetDateTime getDischargeDecidedAt() { return dischargeDecidedAt; }
    public void setDischargeDecidedAt(OffsetDateTime dischargeDecidedAt) { this.dischargeDecidedAt = dischargeDecidedAt; }
    public String getDischargeDecidedBy() { return dischargeDecidedBy; }
    public void setDischargeDecidedBy(String dischargeDecidedBy) { this.dischargeDecidedBy = dischargeDecidedBy; }
}
