package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wgv_import_exception")
public class ImportExceptionEntity {
    @Id private UUID id;
    @Column(name = "import_batch_id", nullable = false) private UUID importBatchId;
    @Column(name = "row_id") private UUID rowId;
    @Column(name = "exception_type", nullable = false) private String exceptionType;
    @Column(name = "severity", nullable = false) private String severity;
    @Column(name = "message", columnDefinition = "TEXT") private String message;
    @Column(name = "resolution_status", nullable = false) private String resolutionStatus = "open";
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public ImportExceptionEntity() {}

    public void init(UUID batchId, UUID rowId, String type, String severity, String message) {
        this.id = UUID.randomUUID();
        this.importBatchId = batchId;
        this.rowId = rowId;
        this.exceptionType = type;
        this.severity = severity;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getImportBatchId() { return importBatchId; }
    public UUID getRowId() { return rowId; }
    public String getExceptionType() { return exceptionType; }
    public String getSeverity() { return severity; }
    public String getMessage() { return message; }
    public String getResolutionStatus() { return resolutionStatus; }
    public Instant getCreatedAt() { return createdAt; }
}
