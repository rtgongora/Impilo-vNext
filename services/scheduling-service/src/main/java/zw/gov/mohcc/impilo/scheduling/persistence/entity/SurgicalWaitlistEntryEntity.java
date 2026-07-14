package zw.gov.mohcc.impilo.scheduling.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Surgical waitlist entry — the single owner of "who is waiting". Fed only by
 * referral.surgical.accepted (idempotent by referral_id). {@code wait_days} and
 * {@code escalation_state} are NEVER stored; they are computed at read time so
 * the escalation view is never stale.
 */
@Entity
@Table(name = "surgical_waitlist_entry", schema = "scheduling")
public class SurgicalWaitlistEntryEntity {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_REMOVED = "REMOVED";
    public static final String STATUS_DEFERRED = "DEFERRED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "referral_id", nullable = false)
    private UUID referralId;

    @Column(name = "patient_id", nullable = false)
    private String patientId;

    @Column(name = "zibo")
    private String zibo;

    @Column(name = "clinical_priority", nullable = false)
    private String clinicalPriority = "P4";

    @Column(name = "target_timeframe_days", nullable = false)
    private int targetTimeframeDays = 90;

    @Column(name = "listed_at", nullable = false)
    private OffsetDateTime listedAt;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "special_requirements", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> specialRequirements = new LinkedHashMap<>();

    @Column(name = "status", nullable = false)
    private String status = STATUS_WAITING;

    @Column(name = "scheduled_or_list_item_id")
    private UUID scheduledOrListItemId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "history", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> history = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SurgicalWaitlistEntryEntity() {
    }

    public SurgicalWaitlistEntryEntity(UUID id, UUID tenantId, UUID referralId, String patientId) {
        this.id = id;
        this.tenantId = tenantId;
        this.referralId = referralId;
        this.patientId = patientId;
        this.listedAt = OffsetDateTime.now();
    }

    @PrePersist
    @PreUpdate
    void touch() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (listedAt == null) {
            listedAt = now;
        }
        updatedAt = now;
        if (specialRequirements == null) {
            specialRequirements = new LinkedHashMap<>();
        }
        if (history == null) {
            history = new ArrayList<>();
        }
        if (targetDate == null && listedAt != null) {
            targetDate = listedAt.toLocalDate().plusDays(targetTimeframeDays);
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

    /** Escalation state is derived, never stored — always fresh at read. */
    public String escalationState(LocalDate today) {
        if (targetDate == null) {
            return "ON_TARGET";
        }
        long remaining = java.time.temporal.ChronoUnit.DAYS.between(today, targetDate);
        if (remaining < 0) {
            return "BREACHED";
        }
        if (remaining <= Math.max(7, targetTimeframeDays / 10)) {
            return "APPROACHING";
        }
        return "ON_TARGET";
    }

    public long waitDays(LocalDate today) {
        if (listedAt == null) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(listedAt.toLocalDate(), today);
    }

    public Map<String, Object> toEnvelope(LocalDate today) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id.toString());
        out.put("tenantId", tenantId.toString());
        out.put("referralId", referralId.toString());
        out.put("patientId", patientId);
        out.put("zibo", zibo);
        out.put("clinicalPriority", clinicalPriority);
        out.put("targetTimeframeDays", targetTimeframeDays);
        out.put("listedAt", listedAt != null ? listedAt.toString() : null);
        out.put("targetDate", targetDate != null ? targetDate.toString() : null);
        out.put("waitDays", waitDays(today));
        out.put("escalationState", escalationState(today));
        out.put("specialRequirements", specialRequirements);
        out.put("status", status);
        out.put("scheduledOrListItemId", scheduledOrListItemId != null ? scheduledOrListItemId.toString() : null);
        out.put("history", history);
        return out;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getReferralId() {
        return referralId;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getZibo() {
        return zibo;
    }

    public String getClinicalPriority() {
        return clinicalPriority;
    }

    public int getTargetTimeframeDays() {
        return targetTimeframeDays;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public String getStatus() {
        return status;
    }

    public UUID getScheduledOrListItemId() {
        return scheduledOrListItemId;
    }

    public Map<String, Object> getSpecialRequirements() {
        return specialRequirements;
    }

    public void setZibo(String zibo) {
        this.zibo = zibo;
    }

    public void setClinicalPriority(String clinicalPriority) {
        if (clinicalPriority != null && !clinicalPriority.isBlank()) {
            this.clinicalPriority = clinicalPriority;
        }
    }

    public void setTargetTimeframeDays(int targetTimeframeDays) {
        this.targetTimeframeDays = targetTimeframeDays;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public void setSpecialRequirements(Map<String, Object> specialRequirements) {
        this.specialRequirements = specialRequirements != null ? specialRequirements : new LinkedHashMap<>();
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setScheduledOrListItemId(UUID scheduledOrListItemId) {
        this.scheduledOrListItemId = scheduledOrListItemId;
    }
}
