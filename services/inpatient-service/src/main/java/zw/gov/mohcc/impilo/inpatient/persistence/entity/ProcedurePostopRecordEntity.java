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
}
