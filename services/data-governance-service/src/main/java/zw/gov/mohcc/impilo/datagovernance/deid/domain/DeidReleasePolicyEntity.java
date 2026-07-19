package zw.gov.mohcc.impilo.datagovernance.deid.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Data-use release policy for de-identified datasets (design principle 5).
 *
 * <p>The release gate is deny-by-default: a run only reaches {@code RELEASED}
 * when an {@code ACTIVE} policy matching the dataset purpose (and the dataset's
 * {@code policy_ref}, when set) exists at gate time.</p>
 */
@Entity
@Table(name = "deid_release_policy")
public class DeidReleasePolicyEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @Column(name = "policy_id", nullable = false)
    private UUID policyId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "policy_code", nullable = false, length = 128)
    private String policyCode;

    @Column(name = "purpose", nullable = false, length = 64)
    private String purpose;

    @Column(name = "requester", length = 255)
    private String requester;

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "reid_prohibited", nullable = false)
    private boolean reidProhibited = true;

    @Column(name = "k_threshold", nullable = false)
    private int kThreshold = 5;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quasi_identifier_rules", columnDefinition = "jsonb")
    private String quasiIdentifierRulesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowlist_columns", columnDefinition = "jsonb")
    private String allowlistColumnsJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status = STATUS_DRAFT;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected DeidReleasePolicyEntity() {}

    public DeidReleasePolicyEntity(UUID tenantId, String policyCode, String purpose) {
        this.tenantId = tenantId;
        this.policyCode = policyCode;
        this.purpose = purpose;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public UUID getPolicyId() { return policyId; }
    public UUID getTenantId() { return tenantId; }
    public String getPolicyCode() { return policyCode; }
    public String getPurpose() { return purpose; }
    public String getRequester() { return requester; }
    public Integer getRetentionDays() { return retentionDays; }
    public boolean isReidProhibited() { return reidProhibited; }
    public int getKThreshold() { return kThreshold; }
    public String getQuasiIdentifierRulesJson() { return quasiIdentifierRulesJson; }
    public String getAllowlistColumnsJson() { return allowlistColumnsJson; }
    public String getStatus() { return status; }
    public int getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setPurpose(String purpose) { this.purpose = purpose; }
    public void setRequester(String requester) { this.requester = requester; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }
    public void setReidProhibited(boolean reidProhibited) { this.reidProhibited = reidProhibited; }
    public void setKThreshold(int kThreshold) { this.kThreshold = kThreshold; }
    public void setQuasiIdentifierRulesJson(String quasiIdentifierRulesJson) { this.quasiIdentifierRulesJson = quasiIdentifierRulesJson; }
    public void setAllowlistColumnsJson(String allowlistColumnsJson) { this.allowlistColumnsJson = allowlistColumnsJson; }
    public void setStatus(String status) { this.status = status; }
    public void setVersion(int version) { this.version = version; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
