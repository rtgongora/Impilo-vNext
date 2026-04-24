package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ind_checklist_templates")
public class InspectionChecklistTemplateEntity {

    @Id
    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "applicable_site_category", length = 64)
    private String applicableSiteCategory;

    @Column(name = "applicable_inspection_type", nullable = false, length = 32)
    private String applicableInspectionType;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected InspectionChecklistTemplateEntity() {}

    public InspectionChecklistTemplateEntity(UUID templateId, UUID tenantId, String code, int version, String name, String applicableInspectionType) {
        this.templateId = templateId;
        this.tenantId = tenantId;
        this.code = code;
        this.version = version;
        this.name = name;
        this.applicableInspectionType = applicableInspectionType;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getTemplateId() { return templateId; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public int getVersion() { return version; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.updatedAt = OffsetDateTime.now(); }
    public String getApplicableSiteCategory() { return applicableSiteCategory; }
    public void setApplicableSiteCategory(String applicableSiteCategory) { this.applicableSiteCategory = applicableSiteCategory; this.updatedAt = OffsetDateTime.now(); }
    public String getApplicableInspectionType() { return applicableInspectionType; }
    public boolean isActiveFlag() { return activeFlag; }
    public void setActiveFlag(boolean activeFlag) { this.activeFlag = activeFlag; this.updatedAt = OffsetDateTime.now(); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

