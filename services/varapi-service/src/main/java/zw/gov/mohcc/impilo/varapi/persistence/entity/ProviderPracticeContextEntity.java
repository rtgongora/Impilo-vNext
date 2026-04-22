package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Provider practice context authorization entity.
 * Tracks where and how a provider is authorized to practice.
 */
@Entity
@Table(name = "provider_practice_contexts", schema = "varapi")
public class ProviderPracticeContextEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "context_type", nullable = false, length = 50)
    private String contextType;

    @Column(name = "linked_facility_id")
    private Long linkedFacilityId;

    @Column(name = "service_scope_summary", columnDefinition = "TEXT")
    private String serviceScopeSummary;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status", length = 20)
    private String status = "REQUESTED";

    @Column(name = "authorisation_basis", length = 255)
    private String authorisationBasis;

    @Column(name = "location_code", length = 50)
    private String locationCode;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "decision_date")
    private LocalDate decisionDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "authorization_reference", length = 100)
    private String authorizationReference;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProviderEntity getProvider() { return provider; }
    public void setProvider(ProviderEntity provider) { this.provider = provider; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getContextType() { return contextType; }
    public void setContextType(String contextType) { this.contextType = contextType; }

    public Long getLinkedFacilityId() { return linkedFacilityId; }
    public void setLinkedFacilityId(Long linkedFacilityId) { this.linkedFacilityId = linkedFacilityId; }

    public String getServiceScopeSummary() { return serviceScopeSummary; }
    public void setServiceScopeSummary(String serviceScopeSummary) { this.serviceScopeSummary = serviceScopeSummary; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAuthorisationBasis() { return authorisationBasis; }
    public void setAuthorisationBasis(String authorisationBasis) { this.authorisationBasis = authorisationBasis; }

    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public LocalDate getDecisionDate() { return decisionDate; }
    public void setDecisionDate(LocalDate decisionDate) { this.decisionDate = decisionDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getAuthorizationReference() { return authorizationReference; }
    public void setAuthorizationReference(String authorizationReference) { this.authorizationReference = authorizationReference; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
