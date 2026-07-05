package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A national-level appointment (e.g. NATIONAL_ADMINISTRATOR) within a country
 * operation. Per doctrine, every appointment record carries its source
 * (PLATFORM_ORIGIN or DELEGATED), the appointing actor, appointment date,
 * optional expiry, an explicit scope document, and a revocation trail.
 *
 * <p>Appointments and revocations execute ONLY via APPROVED
 * {@code PLATFORM_ACTION} access requests under the two-person rule.</p>
 */
@Entity
@Table(name = "wgv_national_appointment")
public class NationalAppointmentEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "country_operation_id", nullable = false)
    private UUID countryOperationId;

    @Column(name = "subject_user_id", nullable = false)
    private String subjectUserId;

    @Column(name = "role", nullable = false, length = 64)
    private String role = "NATIONAL_ADMINISTRATOR";

    @Column(name = "appointment_source", nullable = false, length = 32)
    private String appointmentSource = "PLATFORM_ORIGIN";

    @Column(name = "appointed_by", nullable = false)
    private String appointedBy;

    @Column(name = "appointed_at", nullable = false)
    private Instant appointedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "scope", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> scope;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "revocation_reason", columnDefinition = "TEXT")
    private String revocationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (appointedAt == null) appointedAt = Instant.now();
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCountryOperationId() { return countryOperationId; }
    public void setCountryOperationId(UUID countryOperationId) { this.countryOperationId = countryOperationId; }
    public String getSubjectUserId() { return subjectUserId; }
    public void setSubjectUserId(String subjectUserId) { this.subjectUserId = subjectUserId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getAppointmentSource() { return appointmentSource; }
    public void setAppointmentSource(String appointmentSource) { this.appointmentSource = appointmentSource; }
    public String getAppointedBy() { return appointedBy; }
    public void setAppointedBy(String appointedBy) { this.appointedBy = appointedBy; }
    public Instant getAppointedAt() { return appointedAt; }
    public void setAppointedAt(Instant appointedAt) { this.appointedAt = appointedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Map<String, Object> getScope() { return scope; }
    public void setScope(Map<String, Object> scope) { this.scope = scope; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRevocationReason() { return revocationReason; }
    public void setRevocationReason(String revocationReason) { this.revocationReason = revocationReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
