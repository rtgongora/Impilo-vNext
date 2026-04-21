package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "facility_status_history", schema = "tuso")
public class FacilityStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private FacilityEntity facility;

    @Enumerated(EnumType.STRING)
    @Column(name = "regulatory_status", nullable = false, length = 64)
    private FacilityRegulatoryStatus regulatoryStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_state", length = 64)
    private FacilityApplicationState applicationState;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "changed_by", nullable = false, length = 255)
    private String changedBy;

    @Column(name = "authority_context", length = 128)
    private String authorityContext;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "source_application_id")
    private UUID sourceApplicationId;

    @PrePersist
    void onCreate() {
        changedAt = Instant.now();
    }

    public Long getId() { return id; }
    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }
    public FacilityRegulatoryStatus getRegulatoryStatus() { return regulatoryStatus; }
    public void setRegulatoryStatus(FacilityRegulatoryStatus regulatoryStatus) { this.regulatoryStatus = regulatoryStatus; }
    public FacilityApplicationState getApplicationState() { return applicationState; }
    public void setApplicationState(FacilityApplicationState applicationState) { this.applicationState = applicationState; }
    public Instant getChangedAt() { return changedAt; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public String getAuthorityContext() { return authorityContext; }
    public void setAuthorityContext(String authorityContext) { this.authorityContext = authorityContext; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public UUID getSourceApplicationId() { return sourceApplicationId; }
    public void setSourceApplicationId(UUID sourceApplicationId) { this.sourceApplicationId = sourceApplicationId; }
}
