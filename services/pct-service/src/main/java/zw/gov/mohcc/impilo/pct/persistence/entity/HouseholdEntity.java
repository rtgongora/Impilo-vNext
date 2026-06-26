package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A community-context household (PCT) — the registry the provider-app outreach screens read/write. */
@Entity
@Table(name = "pct_households")
public class HouseholdEntity {

    @Id
    @Column(name = "household_id")
    private UUID householdId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "head_of_household", nullable = false)
    private String headOfHousehold;

    @Column(name = "address")
    private String address;

    @Column(name = "gps_latitude")
    private Double gpsLatitude;

    @Column(name = "gps_longitude")
    private Double gpsLongitude;

    @Column(name = "risk_category", nullable = false)
    private String riskCategory = "STANDARD";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "members", nullable = false, columnDefinition = "jsonb")
    private String members = "[]";

    @Column(name = "last_visit_date")
    private LocalDate lastVisitDate;

    @Column(name = "next_visit_date")
    private LocalDate nextVisitDate;

    @Column(name = "offline_id")
    private String offlineId;

    @Column(name = "registered_by", nullable = false)
    private String registeredBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (householdId == null) householdId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getHeadOfHousehold() { return headOfHousehold; }
    public void setHeadOfHousehold(String headOfHousehold) { this.headOfHousehold = headOfHousehold; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Double getGpsLatitude() { return gpsLatitude; }
    public void setGpsLatitude(Double gpsLatitude) { this.gpsLatitude = gpsLatitude; }

    public Double getGpsLongitude() { return gpsLongitude; }
    public void setGpsLongitude(Double gpsLongitude) { this.gpsLongitude = gpsLongitude; }

    public String getRiskCategory() { return riskCategory; }
    public void setRiskCategory(String riskCategory) { this.riskCategory = riskCategory; }

    public String getMembers() { return members; }
    public void setMembers(String members) { this.members = members; }

    public LocalDate getLastVisitDate() { return lastVisitDate; }
    public void setLastVisitDate(LocalDate lastVisitDate) { this.lastVisitDate = lastVisitDate; }

    public LocalDate getNextVisitDate() { return nextVisitDate; }
    public void setNextVisitDate(LocalDate nextVisitDate) { this.nextVisitDate = nextVisitDate; }

    public String getOfflineId() { return offlineId; }
    public void setOfflineId(String offlineId) { this.offlineId = offlineId; }

    public String getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(String registeredBy) { this.registeredBy = registeredBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
