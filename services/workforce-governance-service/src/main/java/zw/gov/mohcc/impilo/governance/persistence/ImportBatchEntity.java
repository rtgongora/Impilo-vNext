package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "wgv_import_batch")
public class ImportBatchEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "organisation_id", nullable = false) private UUID organisationId;
    @Column(name = "import_type", nullable = false) private String importType;
    @Column(name = "status", nullable = false) private String status;
    @Column(name = "uploaded_by_user_id", nullable = false) private String uploadedByUserId;
    @Column(name = "source_file_name") private String sourceFileName;
    @Column(name = "file_hash") private String fileHash;
    @Column(name = "row_count") private int rowCount;
    @Column(name = "valid_row_count") private int validRowCount;
    @Column(name = "exception_count") private int exceptionCount;
    @Column(name = "duplicate_count") private int duplicateCount;
    @Column(name = "ready_to_invite_count") private int readyToInviteCount;
    @Column(name = "created_access_request_count") private int createdAccessRequestCount;
    @Column(name = "source_of_record_status") private String sourceOfRecordStatus = "workforce_governance";
    @Column(name = "audit_status") private String auditStatus;
    @Column(name = "stage") private String stage;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_mapping") private Map<String, String> columnMapping;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "metadata", columnDefinition = "TEXT") private String metadata;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public ImportBatchEntity() {}

    public void init(UUID tenantId, UUID organisationId, String importType, String uploadedBy, String fileName, String fileHash, int rowCount) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.organisationId = organisationId;
        this.importType = importType;
        this.status = "uploaded";
        this.uploadedByUserId = uploadedBy;
        this.sourceFileName = fileName;
        this.fileHash = fileHash;
        this.rowCount = rowCount;
        this.stage = "UPLOADED";
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.expiresAt = now.plusSeconds(90L * 24 * 3600);
    }

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getImportType() { return importType; }
    public int getRowCount() { return rowCount; }
    public int getValidRowCount() { return validRowCount; }
    public int getExceptionCount() { return exceptionCount; }
    public int getDuplicateCount() { return duplicateCount; }
    public int getReadyToInviteCount() { return readyToInviteCount; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; touch(); }
    public Map<String, String> getColumnMapping() { return columnMapping; }
    public void setColumnMapping(Map<String, String> columnMapping) { this.columnMapping = columnMapping; touch(); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }
    public void setAuditStatus(String auditStatus) { this.auditStatus = auditStatus; touch(); }
    public void setCounts(int valid, int exceptions, int duplicates, int ready, int accessRequests) {
        this.validRowCount = valid;
        this.exceptionCount = exceptions;
        this.duplicateCount = duplicates;
        this.readyToInviteCount = ready;
        this.createdAccessRequestCount = accessRequests;
        touch();
    }
    public void setReadyToInviteCount(int readyToInviteCount) { this.readyToInviteCount = readyToInviteCount; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
