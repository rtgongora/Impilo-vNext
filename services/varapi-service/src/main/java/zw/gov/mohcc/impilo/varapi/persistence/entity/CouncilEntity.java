package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "councils", schema = "varapi")
public class CouncilEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "council_code", nullable = false, unique = true, length = 50)
    private String councilCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "council_type", length = 30)
    private String councilType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "website", length = 255)
    private String website;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    /** Optional Java regex pattern for validating council registration numbers (collaboration). */
    @Column(name = "registration_number_pattern", length = 512)
    private String registrationNumberPattern;

    /** FK to org_registry organisation identity (ROM R1). Null only for legacy non-ROM rows. */
    @Column(name = "org_registry_org_id")
    private UUID orgRegistryOrgId;

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

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getCouncilCode() { return councilCode; }
    public void setCouncilCode(String councilCode) { this.councilCode = councilCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCouncilType() { return councilType; }
    public void setCouncilType(String councilType) { this.councilType = councilType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRegistrationNumberPattern() { return registrationNumberPattern; }
    public void setRegistrationNumberPattern(String registrationNumberPattern) {
        this.registrationNumberPattern = registrationNumberPattern;
    }

    public UUID getOrgRegistryOrgId() { return orgRegistryOrgId; }
    public void setOrgRegistryOrgId(UUID orgRegistryOrgId) { this.orgRegistryOrgId = orgRegistryOrgId; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
