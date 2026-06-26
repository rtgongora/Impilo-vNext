package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A community screening record (PCT). Supports offline reconciliation. */
@Entity
@Table(name = "pct_community_screenings")
public class CommunityScreeningEntity {

    @Id
    @Column(name = "screening_id")
    private UUID screeningId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "household_id")
    private UUID householdId;

    @Column(name = "visit_id")
    private UUID visitId;

    @Column(name = "patient_id", nullable = false)
    private String patientId;

    @Column(name = "screening_type", nullable = false)
    private String screeningType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "results", nullable = false, columnDefinition = "jsonb")
    private String results = "{}";

    @Column(name = "referral_made", nullable = false)
    private boolean referralMade;

    @Column(name = "captured_offline", nullable = false)
    private boolean capturedOffline;

    @Column(name = "offline_id")
    private String offlineId;

    @Column(name = "screened_by", nullable = false)
    private String screenedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (screeningId == null) screeningId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getScreeningId() { return screeningId; }
    public void setScreeningId(UUID screeningId) { this.screeningId = screeningId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }

    public UUID getVisitId() { return visitId; }
    public void setVisitId(UUID visitId) { this.visitId = visitId; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public String getScreeningType() { return screeningType; }
    public void setScreeningType(String screeningType) { this.screeningType = screeningType; }

    public String getResults() { return results; }
    public void setResults(String results) { this.results = results; }

    public boolean isReferralMade() { return referralMade; }
    public void setReferralMade(boolean referralMade) { this.referralMade = referralMade; }

    public boolean isCapturedOffline() { return capturedOffline; }
    public void setCapturedOffline(boolean capturedOffline) { this.capturedOffline = capturedOffline; }

    public String getOfflineId() { return offlineId; }
    public void setOfflineId(String offlineId) { this.offlineId = offlineId; }

    public String getScreenedBy() { return screenedBy; }
    public void setScreenedBy(String screenedBy) { this.screenedBy = screenedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
