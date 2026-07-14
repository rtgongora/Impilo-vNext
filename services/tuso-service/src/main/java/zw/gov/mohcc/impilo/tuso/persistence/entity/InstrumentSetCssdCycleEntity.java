package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One CSSD decontamination cycle of an instrument set: the audit log of a set's journey
 * USE → SOILED → REPROCESSING → STERILISED → STERILE. TUSO is the SoR for the set + its cycle history.
 */
@Entity
@Table(name = "instrument_set_cssd_cycle", schema = "tuso")
public class InstrumentSetCssdCycleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "instrument_set_id", nullable = false)
    private UUID instrumentSetId;

    @Column(name = "cycle_no", nullable = false)
    private int cycleNo;

    @Column(name = "cycle_state", nullable = false, length = 24)
    private String cycleState = "USE";

    @Column(name = "episode_ref", length = 128)
    private String episodeRef;

    @Column(name = "facility_id")
    private Long facilityId;

    @Column(name = "issued_to", length = 128)
    private String issuedTo;

    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @Column(name = "autoclave_ref", length = 128)
    private String autoclaveRef;

    @Column(name = "operator", length = 128)
    private String operator;

    @Column(name = "reprocessing_at")
    private OffsetDateTime reprocessingAt;

    @Column(name = "sterilised_at")
    private OffsetDateTime sterilisedAt;

    @Column(name = "sterile_until")
    private OffsetDateTime sterileUntil;

    @Column(name = "contaminated", nullable = false)
    private boolean contaminated = false;

    @Column(name = "incomplete", nullable = false)
    private boolean incomplete = false;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id != null ? id.toString() : null);
        out.put("instrumentSetId", instrumentSetId != null ? instrumentSetId.toString() : null);
        out.put("cycleNo", cycleNo);
        out.put("cycleState", cycleState);
        out.put("episodeRef", episodeRef);
        out.put("facilityId", facilityId);
        out.put("issuedTo", issuedTo);
        out.put("issuedAt", issuedAt != null ? issuedAt.toString() : null);
        out.put("returnedAt", returnedAt != null ? returnedAt.toString() : null);
        out.put("autoclaveRef", autoclaveRef);
        out.put("operator", operator);
        out.put("sterileUntil", sterileUntil != null ? sterileUntil.toString() : null);
        out.put("contaminated", contaminated);
        out.put("incomplete", incomplete);
        out.put("notes", notes);
        return out;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getInstrumentSetId() { return instrumentSetId; }
    public void setInstrumentSetId(UUID instrumentSetId) { this.instrumentSetId = instrumentSetId; }
    public int getCycleNo() { return cycleNo; }
    public void setCycleNo(int cycleNo) { this.cycleNo = cycleNo; }
    public String getCycleState() { return cycleState; }
    public void setCycleState(String cycleState) { this.cycleState = cycleState; }
    public String getEpisodeRef() { return episodeRef; }
    public void setEpisodeRef(String episodeRef) { this.episodeRef = episodeRef; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public String getIssuedTo() { return issuedTo; }
    public void setIssuedTo(String issuedTo) { this.issuedTo = issuedTo; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }
    public OffsetDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(OffsetDateTime returnedAt) { this.returnedAt = returnedAt; }
    public String getAutoclaveRef() { return autoclaveRef; }
    public void setAutoclaveRef(String autoclaveRef) { this.autoclaveRef = autoclaveRef; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public OffsetDateTime getReprocessingAt() { return reprocessingAt; }
    public void setReprocessingAt(OffsetDateTime reprocessingAt) { this.reprocessingAt = reprocessingAt; }
    public OffsetDateTime getSterilisedAt() { return sterilisedAt; }
    public void setSterilisedAt(OffsetDateTime sterilisedAt) { this.sterilisedAt = sterilisedAt; }
    public OffsetDateTime getSterileUntil() { return sterileUntil; }
    public void setSterileUntil(OffsetDateTime sterileUntil) { this.sterileUntil = sterileUntil; }
    public boolean isContaminated() { return contaminated; }
    public void setContaminated(boolean contaminated) { this.contaminated = contaminated; }
    public boolean isIncomplete() { return incomplete; }
    public void setIncomplete(boolean incomplete) { this.incomplete = incomplete; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
