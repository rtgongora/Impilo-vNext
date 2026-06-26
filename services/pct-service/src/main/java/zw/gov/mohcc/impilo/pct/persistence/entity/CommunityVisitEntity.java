package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A community/outreach visit (PCT). Supports GPS capture and offline reconciliation. */
@Entity
@Table(name = "pct_community_visits")
public class CommunityVisitEntity {

    @Id
    @Column(name = "visit_id")
    private UUID visitId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "visit_type", nullable = false)
    private String visitType = "HOUSEHOLD_VISIT";

    @Column(name = "status", nullable = false)
    private String status = "COMPLETED";

    @Column(name = "findings")
    private String findings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "referrals", nullable = false, columnDefinition = "jsonb")
    private String referrals = "[]";

    @Column(name = "gps_latitude")
    private Double gpsLatitude;

    @Column(name = "gps_longitude")
    private Double gpsLongitude;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "captured_offline", nullable = false)
    private boolean capturedOffline;

    @Column(name = "offline_id")
    private String offlineId;

    @Column(name = "visited_by", nullable = false)
    private String visitedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (visitId == null) visitId = UUID.randomUUID();
        if (visitDate == null) visitDate = LocalDate.now();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getVisitId() { return visitId; }
    public void setVisitId(UUID visitId) { this.visitId = visitId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getVisitType() { return visitType; }
    public void setVisitType(String visitType) { this.visitType = visitType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFindings() { return findings; }
    public void setFindings(String findings) { this.findings = findings; }

    public String getReferrals() { return referrals; }
    public void setReferrals(String referrals) { this.referrals = referrals; }

    public Double getGpsLatitude() { return gpsLatitude; }
    public void setGpsLatitude(Double gpsLatitude) { this.gpsLatitude = gpsLatitude; }

    public Double getGpsLongitude() { return gpsLongitude; }
    public void setGpsLongitude(Double gpsLongitude) { this.gpsLongitude = gpsLongitude; }

    public LocalDate getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }

    public boolean isCapturedOffline() { return capturedOffline; }
    public void setCapturedOffline(boolean capturedOffline) { this.capturedOffline = capturedOffline; }

    public String getOfflineId() { return offlineId; }
    public void setOfflineId(String offlineId) { this.offlineId = offlineId; }

    public String getVisitedBy() { return visitedBy; }
    public void setVisitedBy(String visitedBy) { this.visitedBy = visitedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
