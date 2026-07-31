package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pct_prior_icu_history")
public class PriorIcuHistoryEntity {
    @Id @Column(name = "entry_id") private UUID entryId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "subject_cpid") private String subjectCpid;
    @Column(name = "approx_year") private Integer approxYear;
    @Column(name = "facility_note") private String facilityNote;
    @Column(name = "reason") private String reason;
    @Column(name = "recorded_by") private String recordedBy;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (entryId == null) entryId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getEntryId() { return entryId; }
    public void setEntryId(UUID v) { entryId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { subjectCpid = v; }
    public Integer getApproxYear() { return approxYear; }
    public void setApproxYear(Integer v) { approxYear = v; }
    public String getFacilityNote() { return facilityNote; }
    public void setFacilityNote(String v) { facilityNote = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { reason = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { recordedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
}
