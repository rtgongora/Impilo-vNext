package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One return to theatre on a procedure episode (V305, pipeline demonstration 10).
 *
 * <p>The detail behind {@code procedure_postop_record.return_to_theatre}, which is a single
 * boolean and therefore cannot say why, cannot say who, and cannot count a second return. The
 * flag stays and is still maintained for existing readers; this is what sits behind it.</p>
 */
@Entity
@Table(name = "procedure_return_to_theatre", schema = "inpatient")
public class ProcedureReturnToTheatreEntity {

    @Id
    @Column(name = "return_id")
    private UUID returnId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    /** 1 for the first return, 2 for the second. What the boolean could never hold. */
    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "returned_at", nullable = false)
    private OffsetDateTime returnedAt;

    @Column(name = "returned_by", nullable = false, length = 128)
    private String returnedBy;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "complication_category", nullable = false, length = 32)
    private String complicationCategory;

    /**
     * A staged second-look booked at the first operation is planned care, not harm. Defaults to
     * false so an unclassified return counts as unplanned — for a safety indicator, over-reporting
     * is the correct direction to be wrong in.
     */
    @Column(name = "planned", nullable = false)
    private boolean planned = false;

    @Column(name = "originating_note_id")
    private UUID originatingNoteId;

    @PrePersist
    void onCreate() {
        if (returnId == null) returnId = UUID.randomUUID();
        if (returnedAt == null) returnedAt = OffsetDateTime.now();
    }

    public UUID getReturnId() { return returnId; }
    public void setReturnId(UUID v) { this.returnId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public int getSeq() { return seq; }
    public void setSeq(int v) { this.seq = v; }
    public OffsetDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(OffsetDateTime v) { this.returnedAt = v; }
    public String getReturnedBy() { return returnedBy; }
    public void setReturnedBy(String v) { this.returnedBy = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getComplicationCategory() { return complicationCategory; }
    public void setComplicationCategory(String v) { this.complicationCategory = v; }
    public boolean isPlanned() { return planned; }
    public void setPlanned(boolean v) { this.planned = v; }
    public UUID getOriginatingNoteId() { return originatingNoteId; }
    public void setOriginatingNoteId(UUID v) { this.originatingNoteId = v; }
}
