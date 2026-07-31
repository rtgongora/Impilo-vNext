package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Confidential safeguarding lane — never exposed via social-history. */
@Entity
@Table(name = "pct_safeguarding_disclosures")
public class SafeguardingDisclosureEntity {
    @Id @Column(name = "disclosure_id") private UUID disclosureId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "subject_cpid") private String subjectCpid;
    @Column(name = "journey_id") private String journeyId;
    @Column(name = "encounter_id") private String encounterId;
    @Column(name = "disclosure_summary") private String disclosureSummary;
    @Column(name = "confidentiality_lane") private String confidentialityLane;
    @Column(name = "recorded_by") private String recordedBy;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (disclosureId == null) disclosureId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (confidentialityLane == null) confidentialityLane = "SAFEGUARDING";
    }

    public UUID getDisclosureId() { return disclosureId; }
    public void setDisclosureId(UUID v) { disclosureId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { subjectCpid = v; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String v) { journeyId = v; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String v) { encounterId = v; }
    public String getDisclosureSummary() { return disclosureSummary; }
    public void setDisclosureSummary(String v) { disclosureSummary = v; }
    public String getConfidentialityLane() { return confidentialityLane; }
    public void setConfidentialityLane(String v) { confidentialityLane = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { recordedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
}
