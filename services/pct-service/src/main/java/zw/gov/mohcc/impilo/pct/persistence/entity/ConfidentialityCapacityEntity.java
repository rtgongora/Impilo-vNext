package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A recorded mature-minor capacity determination (pct V437).
 *
 * <p>Capacity is a fact about a PERSON AT A TIME and per care domain, never a per-record flag —
 * a per-record flag would let two records disagree about the same young person on the same day, and
 * the disagreement would be invisible. Capacity ADDS eligibility for confidentiality; a
 * {@code LACKS_CAPACITY} determination never strips an under-age person of the protection the age
 * rule already gave her.
 */
@Entity
@Table(name = "pct_confidentiality_capacity_determinations")
public class ConfidentialityCapacityEntity {

    public static final String HAS_CAPACITY = "HAS_CAPACITY";
    public static final String LACKS_CAPACITY = "LACKS_CAPACITY";
    public static final String NOT_ASSESSED = "NOT_ASSESSED";

    @Id @Column(name = "determination_id") private UUID determinationId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "subject_cpid") private String subjectCpid;
    @Column(name = "category") private String category;
    @Column(name = "determined_on") private LocalDate determinedOn;
    @Column(name = "capacity") private String capacity;
    @Column(name = "rationale") private String rationale;
    @Column(name = "determined_by") private String determinedBy;
    @Column(name = "recorded_at") private OffsetDateTime recordedAt;

    public UUID getDeterminationId() { return determinationId; }
    public void setDeterminationId(UUID v) { this.determinationId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public LocalDate getDeterminedOn() { return determinedOn; }
    public void setDeterminedOn(LocalDate v) { this.determinedOn = v; }
    public String getCapacity() { return capacity; }
    public void setCapacity(String v) { this.capacity = v; }
    public String getRationale() { return rationale; }
    public void setRationale(String v) { this.rationale = v; }
    public String getDeterminedBy() { return determinedBy; }
    public void setDeterminedBy(String v) { this.determinedBy = v; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime v) { this.recordedAt = v; }
}
