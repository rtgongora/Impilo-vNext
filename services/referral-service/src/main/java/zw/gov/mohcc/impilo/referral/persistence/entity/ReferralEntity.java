package zw.gov.mohcc.impilo.referral.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "referrals", schema = "referral")
public class ReferralEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private String patientId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Column(name = "response_notes")
    private String responseNotes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ReferralEntity() {}

    public ReferralEntity(UUID id, UUID tenantId, String patientId, Map<String, Object> payload) {
        this.id = id;
        this.tenantId = tenantId;
        this.patientId = patientId;
        this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        this.status = "PENDING";
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PrePersist
    @PreUpdate
    void touchTimestamps() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        updatedAt = OffsetDateTime.now();
    }

    public void accept() {
        if (!"PENDING".equals(status) && !"RESPONDED".equals(status)) {
            throw new IllegalStateException("Referral cannot be accepted from status " + status);
        }
        status = "ACCEPTED";
    }

    public void respond(String notes) {
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            throw new IllegalStateException("Referral cannot be responded from status " + status);
        }
        responseNotes = notes;
        status = "RESPONDED";
    }

    public void complete() {
        if (!"COMPLETED".equals(status)) {
            status = "COMPLETED";
        }
    }

    public Map<String, Object> toEnvelope() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", id.toString());
        envelope.put("tenantId", tenantId.toString());
        envelope.put("patientId", patientId);
        envelope.put("status", status);
        envelope.put("payload", payload);
        envelope.put("responseNotes", responseNotes);
        envelope.put("createdAt", createdAt.toString());
        envelope.put("updatedAt", updatedAt.toString());
        return envelope;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPatientId() { return patientId; }
    public String getStatus() { return status; }
}
