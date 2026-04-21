package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "inspection_checklist_template", schema = "tuso")
public class InspectionChecklistTemplateEntity {

    @Id
    @Column(name = "template_id", nullable = false, updatable = false)
    private UUID templateId;

    @Column(name = "code", nullable = false, length = 128, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "facility_type_applicability", length = 100)
    private String facilityTypeApplicability;

    @Column(name = "facility_category_applicability", length = 100)
    private String facilityCategoryApplicability;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_type_applicability", nullable = false, length = 64)
    private FacilityInspectionType inspectionTypeApplicability;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items_json", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> itemsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (templateId == null) {
            templateId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getFacilityTypeApplicability() { return facilityTypeApplicability; }
    public void setFacilityTypeApplicability(String facilityTypeApplicability) { this.facilityTypeApplicability = facilityTypeApplicability; }
    public String getFacilityCategoryApplicability() { return facilityCategoryApplicability; }
    public void setFacilityCategoryApplicability(String facilityCategoryApplicability) { this.facilityCategoryApplicability = facilityCategoryApplicability; }
    public FacilityInspectionType getInspectionTypeApplicability() { return inspectionTypeApplicability; }
    public void setInspectionTypeApplicability(FacilityInspectionType inspectionTypeApplicability) { this.inspectionTypeApplicability = inspectionTypeApplicability; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Map<String, Object>> getItemsJson() { return itemsJson; }
    public void setItemsJson(List<Map<String, Object>> itemsJson) { this.itemsJson = itemsJson; }
    public Instant getCreatedAt() { return createdAt; }
}
