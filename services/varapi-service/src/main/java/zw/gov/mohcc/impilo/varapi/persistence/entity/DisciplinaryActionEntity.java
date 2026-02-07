package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "disciplinary_actions", schema = "varapi")
public class DisciplinaryActionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "council_id")
    private CouncilEntity council;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "action_type", length = 50)
    private String actionType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "imposed_date")
    private LocalDate imposedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "conditions", columnDefinition = "TEXT")
    private String conditions;

    @Column(name = "imposed_by", length = 255)
    private String imposedBy;

    @Column(name = "appeal_ref", length = 255)
    private String appealRef;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

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

    // Convenience methods

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isExpired() {
        return expiryDate != null && LocalDate.now().isAfter(expiryDate);
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProviderEntity getProvider() { return provider; }
    public void setProvider(ProviderEntity provider) { this.provider = provider; }

    public CouncilEntity getCouncil() { return council; }
    public void setCouncil(CouncilEntity council) { this.council = council; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getImposedDate() { return imposedDate; }
    public void setImposedDate(LocalDate imposedDate) { this.imposedDate = imposedDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }

    public String getImposedBy() { return imposedBy; }
    public void setImposedBy(String imposedBy) { this.imposedBy = imposedBy; }

    public String getAppealRef() { return appealRef; }
    public void setAppealRef(String appealRef) { this.appealRef = appealRef; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
