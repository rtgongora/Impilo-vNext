package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_alias", schema = "vito")
public class ClientAliasEntity {

    @Id
    @Column(name = "alias_id", nullable = false, updatable = false)
    private UUID aliasId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_health_id", nullable = false)
    private UUID clientHealthId;

    @Column(name = "alias_type", nullable = false)
    private String aliasType;

    @Column(name = "alias_value", nullable = false)
    private String aliasValue;

    @Column(name = "source")
    private String source;

    @Column(name = "confidence", precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (aliasId == null) {
            aliasId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public UUID getAliasId() { return aliasId; }
    public void setAliasId(UUID aliasId) { this.aliasId = aliasId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getClientHealthId() { return clientHealthId; }
    public void setClientHealthId(UUID clientHealthId) { this.clientHealthId = clientHealthId; }
    public String getAliasType() { return aliasType; }
    public void setAliasType(String aliasType) { this.aliasType = aliasType; }
    public String getAliasValue() { return aliasValue; }
    public void setAliasValue(String aliasValue) { this.aliasValue = aliasValue; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public boolean isActiveFlag() { return activeFlag; }
    public void setActiveFlag(boolean activeFlag) { this.activeFlag = activeFlag; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
