package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A bulk-import batch of a regulator's existing data (ROM-U7). */
@Entity @Table(name = "regulatory_import_batch", schema = "varapi")
public class RegulatoryImportBatchEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "council_id") private Long councilId;
    @Column(name = "record_type", nullable = false) private String recordType;
    @Column(name = "source_label") private String sourceLabel;
    @Column(name = "filename") private String filename;
    @Column(name = "status", nullable = false) private String status = "STAGED";
    @Column(name = "total_rows", nullable = false) private int totalRows = 0;
    @Column(name = "applied_rows", nullable = false) private int appliedRows = 0;
    @Column(name = "uploaded_by") private String uploadedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void oc() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void ou() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID v) { this.tenantId = v; }
    public Long getCouncilId() { return councilId; } public void setCouncilId(Long v) { this.councilId = v; }
    public String getRecordType() { return recordType; } public void setRecordType(String v) { this.recordType = v; }
    public String getSourceLabel() { return sourceLabel; } public void setSourceLabel(String v) { this.sourceLabel = v; }
    public String getFilename() { return filename; } public void setFilename(String v) { this.filename = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public int getTotalRows() { return totalRows; } public void setTotalRows(int v) { this.totalRows = v; }
    public int getAppliedRows() { return appliedRows; } public void setAppliedRows(int v) { this.appliedRows = v; }
    public String getUploadedBy() { return uploadedBy; } public void setUploadedBy(String v) { this.uploadedBy = v; }
}
