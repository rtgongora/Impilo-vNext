package zw.gov.mohcc.impilo.datagovernance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dgv_policy")
public class PolicyEntity {

    @Id
    @Column(name = "policy_id", nullable = false)
    private UUID policyId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "description", length = 1024)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules_json", nullable = false, columnDefinition = "jsonb")
    private String rulesJson = "{}";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected PolicyEntity() {}

    public PolicyEntity(UUID tenantId, String name, int version, String description, String rulesJson) {
        this.tenantId = tenantId;
        this.name = name;
        this.version = version;
        this.description = description;
        this.rulesJson = rulesJson != null ? rulesJson : "{}";
    }

    public UUID getPolicyId() { return policyId; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public int getVersion() { return version; }
    public String getDescription() { return description; }
    public String getRulesJson() { return rulesJson; }
    public String getStatus() { return status; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setStatus(String status) { this.status = status; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
}
