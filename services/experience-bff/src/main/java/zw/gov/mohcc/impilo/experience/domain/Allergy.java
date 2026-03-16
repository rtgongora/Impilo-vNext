package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "allergies")
public class Allergy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    private String allergen;

    @Column(name = "allergen_type")
    private String allergenType;

    private String reaction;

    private String severity;

    private String status;

    @Column(name = "onset_date")
    private LocalDate onsetDate;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Allergy() {}

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public String getAllergen() { return allergen; }
    public String getAllergenType() { return allergenType; }
    public String getReaction() { return reaction; }
    public String getSeverity() { return severity; }
    public String getStatus() { return status; }
    public LocalDate getOnsetDate() { return onsetDate; }
    public String getRecordedBy() { return recordedBy; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
