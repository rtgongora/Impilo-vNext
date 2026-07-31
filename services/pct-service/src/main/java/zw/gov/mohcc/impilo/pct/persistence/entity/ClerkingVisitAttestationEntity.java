package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pct_clerking_visit_attestations")
public class ClerkingVisitAttestationEntity {
    @Id @Column(name = "attestation_id") private UUID attestationId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "subject_cpid") private String subjectCpid;
    @Column(name = "journey_id") private String journeyId;
    @Column(name = "encounter_id") private String encounterId;
    @Column(name = "section_key") private String sectionKey;
    @Column(name = "stance") private String stance;
    @Column(name = "prior_fact_ref") private String priorFactRef;
    @Column(name = "notes") private String notes;
    @Column(name = "recorded_by") private String recordedBy;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (attestationId == null) attestationId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getAttestationId() { return attestationId; }
    public void setAttestationId(UUID v) { attestationId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { subjectCpid = v; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String v) { journeyId = v; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String v) { encounterId = v; }
    public String getSectionKey() { return sectionKey; }
    public void setSectionKey(String v) { sectionKey = v; }
    public String getStance() { return stance; }
    public void setStance(String v) { stance = v; }
    public String getPriorFactRef() { return priorFactRef; }
    public void setPriorFactRef(String v) { priorFactRef = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { recordedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
}
