package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/** One staged row of a bulk import (ROM-U7). Immutable raw + match/review/apply state. */
@Entity @Table(name = "regulatory_import_row", schema = "varapi")
public class RegulatoryImportRowEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "batch_id", nullable = false) private Long batchId;
    @Column(name = "row_index", nullable = false) private int rowIndex;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "raw", nullable = false) private String raw;
    @Column(name = "match_status", nullable = false) private String matchStatus = "UNMATCHED";
    @Column(name = "matched_ref") private String matchedRef;
    @Column(name = "review_status", nullable = false) private String reviewStatus = "PENDING";
    @Column(name = "apply_status", nullable = false) private String applyStatus = "NONE";
    @Column(name = "message") private String message;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void oc() { createdAt = Instant.now(); }
    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID v) { this.tenantId = v; }
    public Long getBatchId() { return batchId; } public void setBatchId(Long v) { this.batchId = v; }
    public int getRowIndex() { return rowIndex; } public void setRowIndex(int v) { this.rowIndex = v; }
    public String getRaw() { return raw; } public void setRaw(String v) { this.raw = v; }
    public String getMatchStatus() { return matchStatus; } public void setMatchStatus(String v) { this.matchStatus = v; }
    public String getMatchedRef() { return matchedRef; } public void setMatchedRef(String v) { this.matchedRef = v; }
    public String getReviewStatus() { return reviewStatus; } public void setReviewStatus(String v) { this.reviewStatus = v; }
    public String getApplyStatus() { return applyStatus; } public void setApplyStatus(String v) { this.applyStatus = v; }
    public String getMessage() { return message; } public void setMessage(String v) { this.message = v; }
}
