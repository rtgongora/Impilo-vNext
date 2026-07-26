package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * What a person wants for their own fertility, recorded rather than inferred.
 *
 * <p>This is the fact that routes everything else: the same woman with the same medical history
 * needs contraceptive counselling, preconception care or fertility investigation depending only on
 * what she wants. It changes over time and the previous answer is clinically relevant to the next
 * conversation, so it is a row with a history and exactly one CURRENT per person.</p>
 */
@Entity
@Table(name = "pct_reproductive_intentions")
public class ReproductiveIntentionEntity {

    public static final String STATUS_CURRENT = "CURRENT";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    public static final String WANTS_PREGNANCY_NOW = "WANTS_PREGNANCY_NOW";
    public static final String WANTS_PREGNANCY_LATER = "WANTS_PREGNANCY_LATER";
    public static final String WANTS_NO_MORE_CHILDREN = "WANTS_NO_MORE_CHILDREN";
    public static final String UNDECIDED = "UNDECIDED";
    /** She was asked and chose not to answer. Distinct from UNDECIDED and from NOT_ASSESSED. */
    public static final String DECLINED_TO_STATE = "DECLINED_TO_STATE";
    public static final String NOT_ASSESSED = "NOT_ASSESSED";

    @Id
    @Column(name = "intention_id")
    private UUID intentionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "journey_id")
    private String journeyId;

    @Column(name = "encounter_id")
    private String encounterId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "intention", nullable = false)
    private String intention;

    @Column(name = "timeframe_months")
    private Integer timeframeMonths;

    @Column(name = "desired_family_size")
    private Integer desiredFamilySize;

    @Column(name = "partner_intention")
    private String partnerIntention;

    @Column(name = "fertility_concern")
    private String fertilityConcern;

    @Column(name = "contraception_desired")
    private String contraceptionDesired;

    @Column(name = "unmet_need")
    private String unmetNeed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "counselling_provided")
    private String counsellingProvided;

    @Column(name = "notes")
    private String notes;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Column(name = "client_offline_id")
    private String clientOfflineId;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UUID getIntentionId() { return intentionId; }
    public void setIntentionId(UUID v) { this.intentionId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String v) { this.journeyId = v; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String v) { this.encounterId = v; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID v) { this.facilityId = v; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime v) { this.recordedAt = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public String getIntention() { return intention; }
    public void setIntention(String v) { this.intention = v; }
    public Integer getTimeframeMonths() { return timeframeMonths; }
    public void setTimeframeMonths(Integer v) { this.timeframeMonths = v; }
    public Integer getDesiredFamilySize() { return desiredFamilySize; }
    public void setDesiredFamilySize(Integer v) { this.desiredFamilySize = v; }
    public String getPartnerIntention() { return partnerIntention; }
    public void setPartnerIntention(String v) { this.partnerIntention = v; }
    public String getFertilityConcern() { return fertilityConcern; }
    public void setFertilityConcern(String v) { this.fertilityConcern = v; }
    public String getContraceptionDesired() { return contraceptionDesired; }
    public void setContraceptionDesired(String v) { this.contraceptionDesired = v; }
    public String getUnmetNeed() { return unmetNeed; }
    public void setUnmetNeed(String v) { this.unmetNeed = v; }
    public String getCounsellingProvided() { return counsellingProvided; }
    public void setCounsellingProvided(String v) { this.counsellingProvided = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public UUID getSupersededBy() { return supersededBy; }
    public void setSupersededBy(UUID v) { this.supersededBy = v; }
    public String getClientOfflineId() { return clientOfflineId; }
    public void setClientOfflineId(String v) { this.clientOfflineId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
