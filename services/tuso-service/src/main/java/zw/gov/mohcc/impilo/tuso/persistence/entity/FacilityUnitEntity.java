package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "facility_unit", schema = "tuso")
public class FacilityUnitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private FacilityEntity facility;

    @Column(name = "unit_type", nullable = false, length = 64)
    private String unitType;

    @Column(name = "service_line", length = 100)
    private String serviceLine;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "registration_required", nullable = false)
    private boolean registrationRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "regulatory_status", nullable = false, length = 64)
    private FacilityRegulatoryStatus regulatoryStatus = FacilityRegulatoryStatus.DRAFT;

    @Column(name = "certificate_status", length = 64)
    private String certificateStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

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
    }

    public Long getId() { return id; }
    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }
    public String getUnitType() { return unitType; }
    public void setUnitType(String unitType) { this.unitType = unitType; }
    public String getServiceLine() { return serviceLine; }
    public void setServiceLine(String serviceLine) { this.serviceLine = serviceLine; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isRegistrationRequired() { return registrationRequired; }
    public void setRegistrationRequired(boolean registrationRequired) { this.registrationRequired = registrationRequired; }
    public FacilityRegulatoryStatus getRegulatoryStatus() { return regulatoryStatus; }
    public void setRegulatoryStatus(FacilityRegulatoryStatus regulatoryStatus) { this.regulatoryStatus = regulatoryStatus; }
    public String getCertificateStatus() { return certificateStatus; }
    public void setCertificateStatus(String certificateStatus) { this.certificateStatus = certificateStatus; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
