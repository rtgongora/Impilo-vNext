package zw.gov.mohcc.impilo.dispatch.domain;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dsp_dispatch_events")
public class DispatchEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notes_json", columnDefinition = "jsonb")
    private String notesJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected DispatchEventEntity() {}

    public DispatchEventEntity(UUID jobId, String status, String notesJson) {
        this.jobId = jobId;
        this.status = status;
        this.notesJson = notesJson;
    }

    public Long getId() { return id; }
    public UUID getJobId() { return jobId; }
    public String getStatus() { return status; }
    public String getNotesJson() { return notesJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
