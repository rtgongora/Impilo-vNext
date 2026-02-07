package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "facility_config_version", schema = "tuso",
       uniqueConstraints = @UniqueConstraint(columnNames = {"facility_id", "version"}))
public class FacilityConfigVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private FacilityEntity facility;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configData;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "rollback_from")
    private Integer rollbackFrom;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Map<String, Object> getConfigData() { return configData; }
    public void setConfigData(Map<String, Object> configData) { this.configData = configData; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }

    public Integer getRollbackFrom() { return rollbackFrom; }
    public void setRollbackFrom(Integer rollbackFrom) { this.rollbackFrom = rollbackFrom; }
}
