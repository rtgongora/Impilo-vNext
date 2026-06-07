package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "donor_pre_screenings", schema = "madi")
public class DonorPreScreeningEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "screening_id", nullable = false, unique = true)
    private UUID screeningId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "donor_id", nullable = false)
    private UUID donorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> answersJson;

    @Column(name = "outcome", nullable = false)
    private String outcome;

    @Column(name = "safe_message", columnDefinition = "TEXT")
    private String safeMessage;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "jurisdiction")
    private String jurisdiction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (screeningId == null) {
            screeningId = UUID.randomUUID();
        }
        if (jurisdiction == null) {
            jurisdiction = "ZW";
        }
        if (answersJson == null) {
            answersJson = Map.of();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getScreeningId() { return screeningId; }
    public void setScreeningId(UUID screeningId) { this.screeningId = screeningId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDonorId() { return donorId; }
    public void setDonorId(UUID donorId) { this.donorId = donorId; }

    public Map<String, Object> getAnswersJson() { return answersJson; }
    public void setAnswersJson(Map<String, Object> answersJson) { this.answersJson = answersJson; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getSafeMessage() { return safeMessage; }
    public void setSafeMessage(String safeMessage) { this.safeMessage = safeMessage; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
