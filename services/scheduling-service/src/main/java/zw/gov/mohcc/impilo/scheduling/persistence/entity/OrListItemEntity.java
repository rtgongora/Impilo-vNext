package zw.gov.mohcc.impilo.scheduling.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One case slot on an OR list. Once the session is published, this item carries
 * the booking_ref and procedure_episode_ref — references to the ONE case source
 * of truth (inpatient.procedure_episode). It never stores a copy of the case.
 */
@Entity
@Table(name = "or_list_item", schema = "scheduling")
public class OrListItemEntity {

    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "waitlist_entry_id")
    private UUID waitlistEntryId;

    @Column(name = "patient_id", nullable = false)
    private String patientId;

    @Column(name = "zibo")
    private String zibo;

    @Column(name = "sequence_position", nullable = false)
    private int sequencePosition = 1;

    @Column(name = "estimated_duration_min", nullable = false)
    private int estimatedDurationMin = 60;

    @Column(name = "surgeon_ref")
    private String surgeonRef;

    @Column(name = "status", nullable = false)
    private String status = STATUS_PLANNED;

    @Column(name = "booking_ref")
    private String bookingRef;

    @Column(name = "procedure_episode_ref")
    private String procedureEpisodeRef;

    @Column(name = "cancel_reason_code")
    private String cancelReasonCode;

    @Column(name = "cancel_reason_text")
    private String cancelReasonText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "history", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> history = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected OrListItemEntity() {
    }

    public OrListItemEntity(UUID id, UUID tenantId, UUID sessionId, String patientId) {
        this.id = id;
        this.tenantId = tenantId;
        this.sessionId = sessionId;
        this.patientId = patientId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (history == null) {
            history = new ArrayList<>();
        }
    }

    public void appendHistory(String action, String actor, String note) {
        if (history == null) {
            history = new ArrayList<>();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("action", action);
        row.put("actor", actor);
        row.put("note", note);
        row.put("at", OffsetDateTime.now().toString());
        history.add(row);
    }

    public Map<String, Object> toEnvelope() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id.toString());
        out.put("tenantId", tenantId.toString());
        out.put("sessionId", sessionId.toString());
        out.put("waitlistEntryId", waitlistEntryId != null ? waitlistEntryId.toString() : null);
        out.put("patientId", patientId);
        out.put("zibo", zibo);
        out.put("sequencePosition", sequencePosition);
        out.put("estimatedDurationMin", estimatedDurationMin);
        out.put("surgeonRef", surgeonRef);
        out.put("status", status);
        out.put("bookingRef", bookingRef);
        out.put("procedureEpisodeRef", procedureEpisodeRef);
        out.put("cancelReasonCode", cancelReasonCode);
        out.put("cancelReasonText", cancelReasonText);
        out.put("history", history);
        return out;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getWaitlistEntryId() {
        return waitlistEntryId;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getZibo() {
        return zibo;
    }

    public int getSequencePosition() {
        return sequencePosition;
    }

    public int getEstimatedDurationMin() {
        return estimatedDurationMin;
    }

    public String getSurgeonRef() {
        return surgeonRef;
    }

    public String getStatus() {
        return status;
    }

    public String getBookingRef() {
        return bookingRef;
    }

    public String getProcedureEpisodeRef() {
        return procedureEpisodeRef;
    }

    public void setWaitlistEntryId(UUID waitlistEntryId) {
        this.waitlistEntryId = waitlistEntryId;
    }

    public void setZibo(String zibo) {
        this.zibo = zibo;
    }

    public void setSequencePosition(int sequencePosition) {
        this.sequencePosition = sequencePosition;
    }

    public void setEstimatedDurationMin(int estimatedDurationMin) {
        this.estimatedDurationMin = estimatedDurationMin;
    }

    public void setSurgeonRef(String surgeonRef) {
        this.surgeonRef = surgeonRef;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setBookingRef(String bookingRef) {
        this.bookingRef = bookingRef;
    }

    public void setProcedureEpisodeRef(String procedureEpisodeRef) {
        this.procedureEpisodeRef = procedureEpisodeRef;
    }

    public void setCancelReason(String code, String text) {
        this.cancelReasonCode = code;
        this.cancelReasonText = text;
    }
}
