package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A country operation — one row per country/tenant activation of the platform
 * ({@code tenant_id} UNIQUE). Created ONLY via an APPROVED {@code PLATFORM_ACTION}
 * access request under the two-person rule; {@code created_by_platform_action}
 * links back to that access request for traceability.
 *
 * <p>Status lifecycle: DRAFT → ACTIVE → SUSPENDED/CLOSED.</p>
 */
@Entity
@Table(name = "wgv_country_operation")
public class CountryOperationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "iso_country_code", nullable = false, length = 2)
    private String isoCountryCode;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "legal_framework", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> legalFramework;

    @Column(name = "sovereign_organisation_id")
    private UUID sovereignOrganisationId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "created_by_platform_action")
    private UUID createdByPlatformAction;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getIsoCountryCode() { return isoCountryCode; }
    public void setIsoCountryCode(String isoCountryCode) { this.isoCountryCode = isoCountryCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Map<String, Object> getLegalFramework() { return legalFramework; }
    public void setLegalFramework(Map<String, Object> legalFramework) { this.legalFramework = legalFramework; }
    public UUID getSovereignOrganisationId() { return sovereignOrganisationId; }
    public void setSovereignOrganisationId(UUID sovereignOrganisationId) { this.sovereignOrganisationId = sovereignOrganisationId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getCreatedByPlatformAction() { return createdByPlatformAction; }
    public void setCreatedByPlatformAction(UUID createdByPlatformAction) { this.createdByPlatformAction = createdByPlatformAction; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
