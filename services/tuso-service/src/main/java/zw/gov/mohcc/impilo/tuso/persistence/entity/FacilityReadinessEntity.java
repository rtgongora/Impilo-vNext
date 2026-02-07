package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "facility_readiness", schema = "tuso")
public class FacilityReadinessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false, unique = true)
    private FacilityEntity facility;

    @Column(name = "connectivity", length = 30)
    private String connectivity;

    @Column(name = "power_source", length = 50)
    private String powerSource;

    @Column(name = "power_backup", nullable = false)
    private Boolean powerBackup = false;

    @Column(name = "device_count", nullable = false)
    private Integer deviceCount = 0;

    @Column(name = "ehr_ready", nullable = false)
    private Boolean ehrReady = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compliance_flags", columnDefinition = "jsonb")
    private Map<String, Object> complianceFlags;

    @Column(name = "assessed_at")
    private Instant assessedAt;

    @Column(name = "assessed_by", length = 255)
    private String assessedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }

    public String getConnectivity() { return connectivity; }
    public void setConnectivity(String connectivity) { this.connectivity = connectivity; }

    public String getPowerSource() { return powerSource; }
    public void setPowerSource(String powerSource) { this.powerSource = powerSource; }

    public Boolean getPowerBackup() { return powerBackup; }
    public void setPowerBackup(Boolean powerBackup) { this.powerBackup = powerBackup; }

    public Integer getDeviceCount() { return deviceCount; }
    public void setDeviceCount(Integer deviceCount) { this.deviceCount = deviceCount; }

    public Boolean getEhrReady() { return ehrReady; }
    public void setEhrReady(Boolean ehrReady) { this.ehrReady = ehrReady; }

    public Map<String, Object> getComplianceFlags() { return complianceFlags; }
    public void setComplianceFlags(Map<String, Object> complianceFlags) { this.complianceFlags = complianceFlags; }

    public Instant getAssessedAt() { return assessedAt; }
    public void setAssessedAt(Instant assessedAt) { this.assessedAt = assessedAt; }

    public String getAssessedBy() { return assessedBy; }
    public void setAssessedBy(String assessedBy) { this.assessedBy = assessedBy; }

    public Instant getUpdatedAt() { return updatedAt; }
}
