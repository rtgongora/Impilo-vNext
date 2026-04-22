package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ind_site_operators")
public class SiteOperatorEntity {

    @Id
    @Column(name = "operator_id", nullable = false)
    private UUID operatorId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "operator_type", nullable = false, length = 32)
    private String operatorType;

    @Column(name = "operator_name", nullable = false, length = 255)
    private String operatorName;

    @Column(name = "contact_phone", length = 64)
    private String contactPhone;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SiteOperatorEntity() {}

    public SiteOperatorEntity(UUID operatorId, UUID tenantId, UUID siteId, String operatorType, String operatorName) {
        this.operatorId = operatorId;
        this.tenantId = tenantId;
        this.siteId = siteId;
        this.operatorType = operatorType;
        this.operatorName = operatorName;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getOperatorId() { return operatorId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSiteId() { return siteId; }
    public String getOperatorType() { return operatorType; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; this.updatedAt = OffsetDateTime.now(); }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; this.updatedAt = OffsetDateTime.now(); }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; this.updatedAt = OffsetDateTime.now(); }
    public boolean isActiveFlag() { return activeFlag; }
    public void setActiveFlag(boolean activeFlag) { this.activeFlag = activeFlag; this.updatedAt = OffsetDateTime.now(); }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; this.updatedAt = OffsetDateTime.now(); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

