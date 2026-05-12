package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_identifier", schema = "vito")
public class ClientIdentifierEntity {

    @Id
    @Column(name = "identifier_id", nullable = false, updatable = false)
    private UUID identifierId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_health_id", nullable = false)
    private UUID clientHealthId;

    @Column(name = "identifier_type", nullable = false)
    private String identifierType;

    @Column(name = "identifier_value", nullable = false)
    private String identifierValue;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "primary_flag", nullable = false)
    private boolean primaryFlag;

    @Column(name = "issue_date")
    private OffsetDateTime issueDate;

    @Column(name = "expiry_date")
    private OffsetDateTime expiryDate;

    @Column(name = "source")
    private String source;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (identifierId == null) {
            identifierId = UUID.randomUUID();
        }
        if (issueDate == null) {
            issueDate = OffsetDateTime.now();
        }
        createdAt = OffsetDateTime.now();
    }

    public UUID getIdentifierId() { return identifierId; }
    public void setIdentifierId(UUID identifierId) { this.identifierId = identifierId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getClientHealthId() { return clientHealthId; }
    public void setClientHealthId(UUID clientHealthId) { this.clientHealthId = clientHealthId; }
    public String getIdentifierType() { return identifierType; }
    public void setIdentifierType(String identifierType) { this.identifierType = identifierType; }
    public String getIdentifierValue() { return identifierValue; }
    public void setIdentifierValue(String identifierValue) { this.identifierValue = identifierValue; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isPrimaryFlag() { return primaryFlag; }
    public void setPrimaryFlag(boolean primaryFlag) { this.primaryFlag = primaryFlag; }
    public OffsetDateTime getIssueDate() { return issueDate; }
    public void setIssueDate(OffsetDateTime issueDate) { this.issueDate = issueDate; }
    public OffsetDateTime getExpiryDate() { return expiryDate; }
    public void setExpiryDate(OffsetDateTime expiryDate) { this.expiryDate = expiryDate; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public boolean isActiveFlag() { return activeFlag; }
    public void setActiveFlag(boolean activeFlag) { this.activeFlag = activeFlag; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
