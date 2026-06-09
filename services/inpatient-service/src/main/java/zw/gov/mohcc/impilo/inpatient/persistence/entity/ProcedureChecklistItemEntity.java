package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "procedure_checklist_item", schema = "inpatient")
public class ProcedureChecklistItemEntity {

    @Id
    @Column(name = "item_id")
    private UUID itemId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "phase", nullable = false)
    private String phase;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "item_label", nullable = false)
    private String itemLabel;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "completed_by")
    private String completedBy;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "notes")
    private String notes;

    @PrePersist
    void onCreate() {
        if (itemId == null) itemId = UUID.randomUUID();
    }

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemLabel() { return itemLabel; }
    public void setItemLabel(String itemLabel) { this.itemLabel = itemLabel; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
