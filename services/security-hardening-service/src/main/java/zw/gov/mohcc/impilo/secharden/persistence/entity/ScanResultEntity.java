package zw.gov.mohcc.impilo.secharden.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "scan_results", schema = "secharden")
public class ScanResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pack_id", nullable = false)
    private Long packId;

    @Column(name = "target", nullable = false)
    private String target;

    @Column(name = "passed", nullable = false)
    private int passed;

    @Column(name = "failed", nullable = false)
    private int failed;

    @Column(name = "skipped", nullable = false)
    private int skipped;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "details", nullable = false, columnDefinition = "jsonb")
    private String details = "[]";

    @Column(name = "scanned_by", nullable = false)
    private String scannedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getPackId() { return packId; }
    public void setPackId(Long packId) { this.packId = packId; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public int getPassed() { return passed; }
    public void setPassed(int passed) { this.passed = passed; }
    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }
    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getScannedBy() { return scannedBy; }
    public void setScannedBy(String scannedBy) { this.scannedBy = scannedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
