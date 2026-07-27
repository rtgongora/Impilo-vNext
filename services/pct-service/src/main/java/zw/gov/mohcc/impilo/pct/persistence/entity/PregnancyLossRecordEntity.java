package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A pregnancy loss — miscarriage, stillbirth, or termination outcome — on the mother's episode.
 *
 * <p><b>There is no baby identity here, by design and by PO ruling.</b> A stillbirth mints no CPID
 * and no newborn record: the loss is the mother's clinical event, and civil notification is an event
 * reference ({@code ubomiNotificationRef}), never a person. The subject is {@code motherCpid}; there
 * is deliberately no column that could hold a baby's identity, the same discipline as the fetus
 * record. If a field is added here that only makes sense for a person, the shape is drifting.
 */
@Entity
@Table(name = "pct_pregnancy_loss_records")
public class PregnancyLossRecordEntity {

    public static final String TYPE_STILLBIRTH_ANTEPARTUM = "STILLBIRTH_ANTEPARTUM";
    public static final String TYPE_STILLBIRTH_INTRAPARTUM = "STILLBIRTH_INTRAPARTUM";
    public static final String TYPE_TERMINATION = "TERMINATION";

    @Id
    @Column(name = "loss_record_id")
    private UUID lossRecordId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "mother_cpid")
    private String motherCpid;

    @Column(name = "pregnancy_episode_id")
    private UUID pregnancyEpisodeId;

    @Column(name = "fetus_id")
    private UUID fetusId;

    @Column(name = "loss_type")
    private String lossType;

    @Column(name = "occurred_on")
    private LocalDate occurredOn;

    @Column(name = "gestation_weeks_at_loss")
    private BigDecimal gestationWeeksAtLoss;

    @Column(name = "gestation_basis")
    private String gestationBasis;

    @Column(name = "cause_category")
    private String causeCategory;

    @Column(name = "cause_detail")
    private String causeDetail;

    @Column(name = "management")
    private String management;

    @Column(name = "stillbirth_certifiable")
    private Boolean stillbirthCertifiable;

    @Column(name = "certificate_issued")
    private Boolean certificateIssued;

    @Column(name = "ubomi_notification_ref")
    private String ubomiNotificationRef;

    @Column(name = "bereavement_support_offered")
    private Boolean bereavementSupportOffered;

    @Column(name = "postloss_contraception_discussed")
    private Boolean postlossContraceptionDiscussed;

    @Column(name = "top_procedure_id")
    private UUID topProcedureId;

    @Column(name = "confidentiality_category")
    private String confidentialityCategory;

    @Column(name = "journey_id")
    private UUID journeyId;

    @Column(name = "encounter_ref")
    private String encounterRef;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "client_offline_id")
    private String clientOfflineId;

    public UUID getLossRecordId() { return lossRecordId; }
    public void setLossRecordId(UUID v) { this.lossRecordId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getMotherCpid() { return motherCpid; }
    public void setMotherCpid(String v) { this.motherCpid = v; }
    public UUID getPregnancyEpisodeId() { return pregnancyEpisodeId; }
    public void setPregnancyEpisodeId(UUID v) { this.pregnancyEpisodeId = v; }
    public UUID getFetusId() { return fetusId; }
    public void setFetusId(UUID v) { this.fetusId = v; }
    public String getLossType() { return lossType; }
    public void setLossType(String v) { this.lossType = v; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public void setOccurredOn(LocalDate v) { this.occurredOn = v; }
    public BigDecimal getGestationWeeksAtLoss() { return gestationWeeksAtLoss; }
    public void setGestationWeeksAtLoss(BigDecimal v) { this.gestationWeeksAtLoss = v; }
    public String getGestationBasis() { return gestationBasis; }
    public void setGestationBasis(String v) { this.gestationBasis = v; }
    public String getCauseCategory() { return causeCategory; }
    public void setCauseCategory(String v) { this.causeCategory = v; }
    public String getCauseDetail() { return causeDetail; }
    public void setCauseDetail(String v) { this.causeDetail = v; }
    public String getManagement() { return management; }
    public void setManagement(String v) { this.management = v; }
    public Boolean getStillbirthCertifiable() { return stillbirthCertifiable; }
    public void setStillbirthCertifiable(Boolean v) { this.stillbirthCertifiable = v; }
    public Boolean getCertificateIssued() { return certificateIssued; }
    public void setCertificateIssued(Boolean v) { this.certificateIssued = v; }
    public String getUbomiNotificationRef() { return ubomiNotificationRef; }
    public void setUbomiNotificationRef(String v) { this.ubomiNotificationRef = v; }
    public Boolean getBereavementSupportOffered() { return bereavementSupportOffered; }
    public void setBereavementSupportOffered(Boolean v) { this.bereavementSupportOffered = v; }
    public Boolean getPostlossContraceptionDiscussed() { return postlossContraceptionDiscussed; }
    public void setPostlossContraceptionDiscussed(Boolean v) { this.postlossContraceptionDiscussed = v; }
    public UUID getTopProcedureId() { return topProcedureId; }
    public void setTopProcedureId(UUID v) { this.topProcedureId = v; }
    public String getConfidentialityCategory() { return confidentialityCategory; }
    public void setConfidentialityCategory(String v) { this.confidentialityCategory = v; }
    public UUID getJourneyId() { return journeyId; }
    public void setJourneyId(UUID v) { this.journeyId = v; }
    public String getEncounterRef() { return encounterRef; }
    public void setEncounterRef(String v) { this.encounterRef = v; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID v) { this.facilityId = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime v) { this.recordedAt = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
    public String getClientOfflineId() { return clientOfflineId; }
    public void setClientOfflineId(String v) { this.clientOfflineId = v; }
}
