package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Links an implanted device unit to the patient/episode it was implanted in.
 * Supports forward recall traceability from a recalled UDI/lot to the patient.
 */
@Entity
@Table(name = "inv_patient_implant")
public class PatientImplantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "patient_implant_id", nullable = false)
    private UUID patientImplantId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "implant_unit_id", nullable = false)
    private UUID implantUnitId;

    @Column(name = "patient_cpid", nullable = false, length = 64)
    private String patientCpid;

    @Column(name = "episode_ref", length = 128)
    private String episodeRef;

    @Column(name = "body_site", length = 128)
    private String bodySite;

    @Column(name = "laterality", length = 16)
    private String laterality;

    @Column(name = "implanted_by", length = 128)
    private String implantedBy;

    @Column(name = "implanted_at")
    private OffsetDateTime implantedAt;

    @Column(name = "status", length = 24)
    private String status = "IMPLANTED";

    @Column(name = "patient_facing_name", length = 255)
    private String patientFacingName;

    @Column(name = "patient_facing_description")
    private String patientFacingDescription;

    @Column(name = "expected_lifespan_months")
    private Integer expectedLifespanMonths;

    @Column(name = "next_review_due_at")
    private OffsetDateTime nextReviewDueAt;

    @Column(name = "removed_at")
    private OffsetDateTime removedAt;

    @Column(name = "removed_by", length = 128)
    private String removedBy;

    @Column(name = "removal_reason")
    private String removalReason;

    @Column(name = "revision_of_patient_implant_id")
    private UUID revisionOfPatientImplantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getPatientImplantId() { return patientImplantId; }
    public void setPatientImplantId(UUID patientImplantId) { this.patientImplantId = patientImplantId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getImplantUnitId() { return implantUnitId; }
    public void setImplantUnitId(UUID implantUnitId) { this.implantUnitId = implantUnitId; }

    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }

    public String getEpisodeRef() { return episodeRef; }
    public void setEpisodeRef(String episodeRef) { this.episodeRef = episodeRef; }

    public String getBodySite() { return bodySite; }
    public void setBodySite(String bodySite) { this.bodySite = bodySite; }

    public String getLaterality() { return laterality; }
    public void setLaterality(String laterality) { this.laterality = laterality; }

    public String getImplantedBy() { return implantedBy; }
    public void setImplantedBy(String implantedBy) { this.implantedBy = implantedBy; }

    public OffsetDateTime getImplantedAt() { return implantedAt; }
    public void setImplantedAt(OffsetDateTime implantedAt) { this.implantedAt = implantedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPatientFacingName() { return patientFacingName; }
    public void setPatientFacingName(String v) { this.patientFacingName = v; }

    public String getPatientFacingDescription() { return patientFacingDescription; }
    public void setPatientFacingDescription(String v) { this.patientFacingDescription = v; }

    public Integer getExpectedLifespanMonths() { return expectedLifespanMonths; }
    public void setExpectedLifespanMonths(Integer v) { this.expectedLifespanMonths = v; }

    public OffsetDateTime getNextReviewDueAt() { return nextReviewDueAt; }
    public void setNextReviewDueAt(OffsetDateTime v) { this.nextReviewDueAt = v; }

    public OffsetDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(OffsetDateTime v) { this.removedAt = v; }

    public String getRemovedBy() { return removedBy; }
    public void setRemovedBy(String v) { this.removedBy = v; }

    public String getRemovalReason() { return removalReason; }
    public void setRemovalReason(String v) { this.removalReason = v; }

    public UUID getRevisionOfPatientImplantId() { return revisionOfPatientImplantId; }
    public void setRevisionOfPatientImplantId(UUID v) { this.revisionOfPatientImplantId = v; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
