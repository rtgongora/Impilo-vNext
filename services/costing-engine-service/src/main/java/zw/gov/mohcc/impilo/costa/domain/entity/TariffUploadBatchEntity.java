package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_tariff_upload_batches")
public class TariffUploadBatchEntity {

    @Id
    @Column(name = "upload_batch_id")
    private UUID uploadBatchId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "uploaded_by", length = 120)
    private String uploadedBy;

    @Column(name = "owner_organisation", length = 300)
    private String ownerOrganisation;

    @Column(name = "proposed_tariff_name", nullable = false, length = 400)
    private String proposedTariffName;

    @Column(name = "proposed_country", length = 80)
    private String proposedCountry;

    @Column(name = "proposed_currency", nullable = false, length = 3)
    private String proposedCurrency = "USD";

    @Column(name = "upload_status", nullable = false, length = 40)
    private String uploadStatus = "RECEIVED";

    @Column(name = "validation_status", nullable = false, length = 40)
    private String validationStatus = "unvalidated";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_mapping", nullable = false, columnDefinition = "jsonb")
    private String columnMapping = "{}";

    @Column(name = "rows_total", nullable = false)
    private int rowsTotal;

    @Column(name = "rows_valid", nullable = false)
    private int rowsValid;

    @Column(name = "rows_with_warnings", nullable = false)
    private int rowsWithWarnings;

    @Column(name = "rows_with_errors", nullable = false)
    private int rowsWithErrors;

    @Column(name = "raw_bytes", columnDefinition = "bytea")
    private byte[] rawBytes;

    @Column(name = "raw_text", columnDefinition = "text")
    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadata = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    void prePersist() {
        if (uploadBatchId == null) {
            uploadBatchId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getUploadBatchId() {
        return uploadBatchId;
    }

    public void setUploadBatchId(UUID uploadBatchId) {
        this.uploadBatchId = uploadBatchId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getOwnerOrganisation() {
        return ownerOrganisation;
    }

    public void setOwnerOrganisation(String ownerOrganisation) {
        this.ownerOrganisation = ownerOrganisation;
    }

    public String getProposedTariffName() {
        return proposedTariffName;
    }

    public void setProposedTariffName(String proposedTariffName) {
        this.proposedTariffName = proposedTariffName;
    }

    public String getProposedCountry() {
        return proposedCountry;
    }

    public void setProposedCountry(String proposedCountry) {
        this.proposedCountry = proposedCountry;
    }

    public String getProposedCurrency() {
        return proposedCurrency;
    }

    public void setProposedCurrency(String proposedCurrency) {
        this.proposedCurrency = proposedCurrency;
    }

    public String getUploadStatus() {
        return uploadStatus;
    }

    public void setUploadStatus(String uploadStatus) {
        this.uploadStatus = uploadStatus;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    public String getColumnMapping() {
        return columnMapping;
    }

    public void setColumnMapping(String columnMapping) {
        this.columnMapping = columnMapping;
    }

    public int getRowsTotal() {
        return rowsTotal;
    }

    public void setRowsTotal(int rowsTotal) {
        this.rowsTotal = rowsTotal;
    }

    public int getRowsValid() {
        return rowsValid;
    }

    public void setRowsValid(int rowsValid) {
        this.rowsValid = rowsValid;
    }

    public int getRowsWithWarnings() {
        return rowsWithWarnings;
    }

    public void setRowsWithWarnings(int rowsWithWarnings) {
        this.rowsWithWarnings = rowsWithWarnings;
    }

    public int getRowsWithErrors() {
        return rowsWithErrors;
    }

    public void setRowsWithErrors(int rowsWithErrors) {
        this.rowsWithErrors = rowsWithErrors;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public void setRawBytes(byte[] rawBytes) {
        this.rawBytes = rawBytes;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
