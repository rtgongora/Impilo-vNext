package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pct_imaging_links", schema = "pct")
public class ImagingLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "journey_id", nullable = false, length = 26)
    private String journeyId;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "encounter_ref")
    private UUID encounterRef;

    @Column(name = "governed_study_id", nullable = false, length = 64)
    private String governedStudyId;

    @Column(name = "oros_order_id", length = 128)
    private String orosOrderId;

    @Column(name = "link_type", nullable = false, length = 64)
    private String linkType = "TRIAGE_IMAGING";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_by", columnDefinition = "TEXT")
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }
    public Long getEncounterId() { return encounterId; }
    public void setEncounterId(Long encounterId) { this.encounterId = encounterId; }
    public UUID getEncounterRef() { return encounterRef; }
    public void setEncounterRef(UUID encounterRef) { this.encounterRef = encounterRef; }
    public String getGovernedStudyId() { return governedStudyId; }
    public void setGovernedStudyId(String governedStudyId) { this.governedStudyId = governedStudyId; }
    public String getOrosOrderId() { return orosOrderId; }
    public void setOrosOrderId(String orosOrderId) { this.orosOrderId = orosOrderId; }
    public String getLinkType() { return linkType; }
    public void setLinkType(String linkType) { this.linkType = linkType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
