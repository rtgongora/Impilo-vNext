package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Required-asset profile for a service point (theatre/lab/ambulance/etc). */
@Entity
@Table(name = "asr_readiness_profile")
public class ReadinessProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_ref", nullable = false, length = 255)
    private String facilityRef;

    @Column(name = "service_point", nullable = false, length = 255)
    private String servicePoint;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    protected ReadinessProfileEntity() {}

    public ReadinessProfileEntity(UUID tenantId, String facilityRef, String servicePoint, String name) {
        this.tenantId = tenantId;
        this.facilityRef = facilityRef;
        this.servicePoint = servicePoint;
        this.name = name;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getProfileId() { return profileId; }
    public UUID getTenantId() { return tenantId; }
    public String getFacilityRef() { return facilityRef; }
    public void setFacilityRef(String facilityRef) { this.facilityRef = facilityRef; }
    public String getServicePoint() { return servicePoint; }
    public void setServicePoint(String servicePoint) { this.servicePoint = servicePoint; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
