package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The birth from the mother's side — the record a normal vaginal birth outside theatre never had.
 *
 * <p>The subject is the mother, not the baby: this is her clinical event and it exists whether the
 * baby lived, died, or was stillborn. The newborn's own record ({@code pct_newborn_birth_records})
 * is a separate, baby-anchored row linked through the shared pregnancy episode.
 *
 * <p>A caesarean row is a thin continuum anchor carrying {@code theatreEpisodeRef}; the operative
 * detail lives in the inpatient theatre episode and is never duplicated here.
 */
@Entity
@Table(name = "pct_delivery_records")
public class DeliveryRecordEntity {

    public static final String STATUS_RECORDED = "RECORDED";
    public static final String STATUS_AMENDED = "AMENDED";
    public static final String STATUS_ENTERED_IN_ERROR = "ENTERED_IN_ERROR";

    public static final String MODE_SPONTANEOUS_VAGINAL = "SPONTANEOUS_VAGINAL";
    public static final String MODE_CAESAREAN = "CAESAREAN";

    @Id
    @Column(name = "delivery_record_id")
    private UUID deliveryRecordId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "mother_cpid")
    private String motherCpid;

    @Column(name = "pregnancy_episode_id")
    private UUID pregnancyEpisodeId;

    @Column(name = "status")
    private String status;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "labour_onset")
    private String labourOnset;

    @Column(name = "delivery_mode")
    private String deliveryMode;

    @Column(name = "theatre_episode_ref")
    private String theatreEpisodeRef;

    @Column(name = "place_of_birth")
    private String placeOfBirth;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "birth_attendant")
    private String birthAttendant;

    @Column(name = "attendant_cadre")
    private String attendantCadre;

    @Column(name = "amtsl_given")
    private Boolean amtslGiven;

    @Column(name = "uterotonic_given")
    private String uterotonicGiven;

    @Column(name = "placenta_delivery")
    private String placentaDelivery;

    @Column(name = "placenta_complete")
    private Boolean placentaComplete;

    @Column(name = "estimated_blood_loss_ml")
    private Integer estimatedBloodLossMl;

    @Column(name = "pph_suspected")
    private Boolean pphSuspected;

    @Column(name = "perineal_outcome")
    private String perinealOutcome;

    @Column(name = "perineal_repair")
    private String perinealRepair;

    @Column(name = "immediate_maternal_condition")
    private String immediateMaternalCondition;

    @Column(name = "babies_delivered")
    private Short babiesDelivered;

    @Column(name = "journey_id")
    private UUID journeyId;

    @Column(name = "encounter_ref")
    private String encounterRef;

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

    public UUID getDeliveryRecordId() { return deliveryRecordId; }
    public void setDeliveryRecordId(UUID v) { this.deliveryRecordId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getMotherCpid() { return motherCpid; }
    public void setMotherCpid(String v) { this.motherCpid = v; }
    public UUID getPregnancyEpisodeId() { return pregnancyEpisodeId; }
    public void setPregnancyEpisodeId(UUID v) { this.pregnancyEpisodeId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(OffsetDateTime v) { this.deliveredAt = v; }
    public String getLabourOnset() { return labourOnset; }
    public void setLabourOnset(String v) { this.labourOnset = v; }
    public String getDeliveryMode() { return deliveryMode; }
    public void setDeliveryMode(String v) { this.deliveryMode = v; }
    public String getTheatreEpisodeRef() { return theatreEpisodeRef; }
    public void setTheatreEpisodeRef(String v) { this.theatreEpisodeRef = v; }
    public String getPlaceOfBirth() { return placeOfBirth; }
    public void setPlaceOfBirth(String v) { this.placeOfBirth = v; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID v) { this.facilityId = v; }
    public String getBirthAttendant() { return birthAttendant; }
    public void setBirthAttendant(String v) { this.birthAttendant = v; }
    public String getAttendantCadre() { return attendantCadre; }
    public void setAttendantCadre(String v) { this.attendantCadre = v; }
    public Boolean getAmtslGiven() { return amtslGiven; }
    public void setAmtslGiven(Boolean v) { this.amtslGiven = v; }
    public String getUterotonicGiven() { return uterotonicGiven; }
    public void setUterotonicGiven(String v) { this.uterotonicGiven = v; }
    public String getPlacentaDelivery() { return placentaDelivery; }
    public void setPlacentaDelivery(String v) { this.placentaDelivery = v; }
    public Boolean getPlacentaComplete() { return placentaComplete; }
    public void setPlacentaComplete(Boolean v) { this.placentaComplete = v; }
    public Integer getEstimatedBloodLossMl() { return estimatedBloodLossMl; }
    public void setEstimatedBloodLossMl(Integer v) { this.estimatedBloodLossMl = v; }
    public Boolean getPphSuspected() { return pphSuspected; }
    public void setPphSuspected(Boolean v) { this.pphSuspected = v; }
    public String getPerinealOutcome() { return perinealOutcome; }
    public void setPerinealOutcome(String v) { this.perinealOutcome = v; }
    public String getPerinealRepair() { return perinealRepair; }
    public void setPerinealRepair(String v) { this.perinealRepair = v; }
    public String getImmediateMaternalCondition() { return immediateMaternalCondition; }
    public void setImmediateMaternalCondition(String v) { this.immediateMaternalCondition = v; }
    public Short getBabiesDelivered() { return babiesDelivered; }
    public void setBabiesDelivered(Short v) { this.babiesDelivered = v; }
    public UUID getJourneyId() { return journeyId; }
    public void setJourneyId(UUID v) { this.journeyId = v; }
    public String getEncounterRef() { return encounterRef; }
    public void setEncounterRef(String v) { this.encounterRef = v; }
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
