package zw.gov.mohcc.impilo.ndila.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ndila_geocode_review_queue")
public class NdilaGeocodeReviewEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "owner_service", nullable = false, length = 32)
    private String ownerService = "TUSO";

    @Column(name = "owner_entity_type", nullable = false, length = 32)
    private String ownerEntityType = "FACILITY";

    @Column(name = "owner_entity_id", nullable = false, length = 64)
    private String ownerEntityId;

    @Column(name = "facility_uid", length = 64)
    private String facilityUid;

    @Column(name = "facility_code", length = 64)
    private String facilityCode;

    @Column(name = "facility_name", nullable = false)
    private String facilityName;

    @Column(name = "province", length = 100)
    private String province;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "reason", nullable = false, length = 64)
    private String reason = "MISSING_COORDINATES";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getOwnerService() { return ownerService; }
    public void setOwnerService(String ownerService) { this.ownerService = ownerService; }
    public String getOwnerEntityType() { return ownerEntityType; }
    public void setOwnerEntityType(String ownerEntityType) { this.ownerEntityType = ownerEntityType; }
    public String getOwnerEntityId() { return ownerEntityId; }
    public void setOwnerEntityId(String ownerEntityId) { this.ownerEntityId = ownerEntityId; }
    public String getFacilityUid() { return facilityUid; }
    public void setFacilityUid(String facilityUid) { this.facilityUid = facilityUid; }
    public String getFacilityCode() { return facilityCode; }
    public void setFacilityCode(String facilityCode) { this.facilityCode = facilityCode; }
    public String getFacilityName() { return facilityName; }
    public void setFacilityName(String facilityName) { this.facilityName = facilityName; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
