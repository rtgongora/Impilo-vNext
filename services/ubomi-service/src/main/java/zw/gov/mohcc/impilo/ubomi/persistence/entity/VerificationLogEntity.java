package zw.gov.mohcc.impilo.ubomi.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "verification_log", schema = "ubomi")
public class VerificationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "registration_number", nullable = false)
    private String registrationNumber;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "requested_by_actor", nullable = false)
    private String requestedByActor;

    @Column(name = "access_mode", nullable = false)
    private String accessMode;

    @Column(name = "verification_result", nullable = false)
    private String verificationResult;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "response_summary", columnDefinition = "jsonb")
    private String responseSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getRequestedByActor() { return requestedByActor; }
    public void setRequestedByActor(String requestedByActor) { this.requestedByActor = requestedByActor; }
    public String getAccessMode() { return accessMode; }
    public void setAccessMode(String accessMode) { this.accessMode = accessMode; }
    public String getVerificationResult() { return verificationResult; }
    public void setVerificationResult(String verificationResult) { this.verificationResult = verificationResult; }
    public String getResponseSummary() { return responseSummary; }
    public void setResponseSummary(String responseSummary) { this.responseSummary = responseSummary; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
