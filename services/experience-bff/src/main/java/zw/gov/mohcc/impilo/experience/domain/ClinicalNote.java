package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "clinical_notes")
public class ClinicalNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "note_type")
    private String noteType;

    private String subjective;

    private String objective;

    private String assessment;

    private String plan;

    private String body;

    @Column(name = "author_id")
    private String authorId;

    @Column(name = "author_name")
    private String authorName;

    private String status;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ClinicalNote() {}

    public void sign() {
        this.status = "SIGNED";
        this.signedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getEncounterId() { return encounterId; }
    public String getNoteType() { return noteType; }
    public String getSubjective() { return subjective; }
    public String getObjective() { return objective; }
    public String getAssessment() { return assessment; }
    public String getPlan() { return plan; }
    public String getBody() { return body; }
    public String getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getStatus() { return status; }
    public OffsetDateTime getSignedAt() { return signedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
