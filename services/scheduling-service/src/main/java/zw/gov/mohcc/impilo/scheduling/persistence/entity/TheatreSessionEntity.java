package zw.gov.mohcc.impilo.scheduling.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A booked OR list for a theatre room on a date. */
@Entity
@Table(name = "theatre_session", schema = "scheduling")
public class TheatreSessionEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private String facilityId;

    @Column(name = "clinical_space_id")
    private UUID clinicalSpaceId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime = LocalTime.of(8, 0);

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime = LocalTime.of(17, 0);

    @Column(name = "slot", nullable = false)
    private String slot = "AM";

    @Column(name = "turnover_minutes", nullable = false)
    private int turnoverMinutes = 30;

    @Column(name = "lead_surgeon_ref")
    private String leadSurgeonRef;

    @Column(name = "vashandi_team_id")
    private UUID vashandiTeamId;

    @Column(name = "status", nullable = false)
    private String status = STATUS_DRAFT;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TheatreSessionEntity() {
    }

    public TheatreSessionEntity(UUID id, UUID tenantId, String facilityId, LocalDate sessionDate) {
        this.id = id;
        this.tenantId = tenantId;
        this.facilityId = facilityId;
        this.sessionDate = sessionDate;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    public Map<String, Object> toEnvelope() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id.toString());
        out.put("tenantId", tenantId.toString());
        out.put("facilityId", facilityId);
        out.put("clinicalSpaceId", clinicalSpaceId != null ? clinicalSpaceId.toString() : null);
        out.put("sessionDate", sessionDate != null ? sessionDate.toString() : null);
        out.put("startTime", startTime != null ? startTime.toString() : null);
        out.put("endTime", endTime != null ? endTime.toString() : null);
        out.put("slot", slot);
        out.put("turnoverMinutes", turnoverMinutes);
        out.put("leadSurgeonRef", leadSurgeonRef);
        out.put("vashandiTeamId", vashandiTeamId != null ? vashandiTeamId.toString() : null);
        out.put("status", status);
        out.put("cancelReason", cancelReason);
        return out;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getFacilityId() {
        return facilityId;
    }

    public UUID getClinicalSpaceId() {
        return clinicalSpaceId;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getSlot() {
        return slot;
    }

    public int getTurnoverMinutes() {
        return turnoverMinutes;
    }

    public String getLeadSurgeonRef() {
        return leadSurgeonRef;
    }

    public UUID getVashandiTeamId() {
        return vashandiTeamId;
    }

    public String getStatus() {
        return status;
    }

    public void setClinicalSpaceId(UUID clinicalSpaceId) {
        this.clinicalSpaceId = clinicalSpaceId;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public void setStartTime(LocalTime startTime) {
        if (startTime != null) {
            this.startTime = startTime;
        }
    }

    public void setEndTime(LocalTime endTime) {
        if (endTime != null) {
            this.endTime = endTime;
        }
    }

    public void setSlot(String slot) {
        if (slot != null && !slot.isBlank()) {
            this.slot = slot;
        }
    }

    public void setTurnoverMinutes(int turnoverMinutes) {
        this.turnoverMinutes = turnoverMinutes;
    }

    public void setLeadSurgeonRef(String leadSurgeonRef) {
        this.leadSurgeonRef = leadSurgeonRef;
    }

    public void setVashandiTeamId(UUID vashandiTeamId) {
        this.vashandiTeamId = vashandiTeamId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }
}
