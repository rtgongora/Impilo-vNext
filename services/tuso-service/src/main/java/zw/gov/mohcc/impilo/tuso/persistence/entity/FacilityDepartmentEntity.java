package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Canonical facility department / organisational-unit identity (TUSO SoR).
 *
 * <p>Distinct from {@link FacilityUnitEntity} (regulatory registration unit),
 * {@link ServicePointEntity} (operational sub-unit / queue anchor) and
 * {@code ClinicalSpaceEntity} (ward/theatre/ICU capacity) — a department is an
 * organisational grouping those entities may optionally attach to via
 * {@code facility_department_id}, never a replacement for them.</p>
 */
@Entity
@Table(name = "facility_department", schema = "tuso")
public class FacilityDepartmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "department_code", nullable = false, length = 64)
    private String departmentCode;

    @Column(name = "official_name", nullable = false, length = 255)
    private String officialName;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "department_type", nullable = false, length = 32)
    private String departmentType = "CLINICAL";

    @Column(name = "parent_department_id")
    private UUID parentDepartmentId;

    @Column(name = "operating_status", nullable = false, length = 32)
    private String operatingStatus = "ACTIVE";

    @Column(name = "opened_at")
    private LocalDate openedAt;

    @Column(name = "closed_at")
    private LocalDate closedAt;

    @Column(name = "head_person_health_id")
    private UUID headPersonHealthId;

    @Column(name = "location_ref", length = 255)
    private String locationRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_json", columnDefinition = "jsonb")
    private Map<String, Object> contact;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "escalation_json", columnDefinition = "jsonb")
    private Map<String, Object> escalation;

    @Column(name = "provenance", nullable = false, length = 32)
    private String provenance = "NATIVE";

    @Column(name = "source_department_ref", length = 128)
    private String sourceDepartmentRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        version += 1;
    }

    public UUID getId() { return id; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getDepartmentCode() { return departmentCode; }
    public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }
    public String getOfficialName() { return officialName; }
    public void setOfficialName(String officialName) { this.officialName = officialName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDepartmentType() { return departmentType; }
    public void setDepartmentType(String departmentType) { this.departmentType = departmentType; }
    public UUID getParentDepartmentId() { return parentDepartmentId; }
    public void setParentDepartmentId(UUID parentDepartmentId) { this.parentDepartmentId = parentDepartmentId; }
    public String getOperatingStatus() { return operatingStatus; }
    public void setOperatingStatus(String operatingStatus) { this.operatingStatus = operatingStatus; }
    public LocalDate getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDate openedAt) { this.openedAt = openedAt; }
    public LocalDate getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDate closedAt) { this.closedAt = closedAt; }
    public UUID getHeadPersonHealthId() { return headPersonHealthId; }
    public void setHeadPersonHealthId(UUID headPersonHealthId) { this.headPersonHealthId = headPersonHealthId; }
    public String getLocationRef() { return locationRef; }
    public void setLocationRef(String locationRef) { this.locationRef = locationRef; }
    public Map<String, Object> getContact() { return contact; }
    public void setContact(Map<String, Object> contact) { this.contact = contact; }
    public Map<String, Object> getEscalation() { return escalation; }
    public void setEscalation(Map<String, Object> escalation) { this.escalation = escalation; }
    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }
    public String getSourceDepartmentRef() { return sourceDepartmentRef; }
    public void setSourceDepartmentRef(String sourceDepartmentRef) { this.sourceDepartmentRef = sourceDepartmentRef; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public int getVersion() { return version; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
