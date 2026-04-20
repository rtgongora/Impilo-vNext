package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a triage assessment record for a patient journey.
 * Captures acuity level, vital signs (as JSONB), and triage notes.
 */
@Entity
@Table(name = "pct_triage_records")
public class TriageRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "journey_id", nullable = false)
    private String journeyId;

    @Column(name = "acuity", nullable = false)
    private int acuity;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "vitals", columnDefinition = "jsonb")
    private String vitals;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "triaged_by", nullable = false)
    private String triagedBy;

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

    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }

    public int getAcuity() { return acuity; }
    public void setAcuity(int acuity) { this.acuity = acuity; }

    public String getVitals() { return vitals; }
    public void setVitals(String vitals) { this.vitals = vitals; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getTriagedBy() { return triagedBy; }
    public void setTriagedBy(String triagedBy) { this.triagedBy = triagedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
