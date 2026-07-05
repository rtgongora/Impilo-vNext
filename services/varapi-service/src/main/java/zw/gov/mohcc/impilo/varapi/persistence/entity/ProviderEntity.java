package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "provider", schema = "varapi")
public class ProviderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_ref", nullable = false, unique = true)
    private UUID providerRef;

    /**
     * Canonical Impilo / Health person identity (UUID). All provider registry state for this human
     * is anchored here; {@link #providerPublicId} and linked identifiers are secondary surfaces.
     */
    @Column(name = "impilo_health_id", nullable = false)
    private UUID impiloHealthId;

    @Column(name = "provider_public_id", nullable = false, unique = true, length = 26)
    private String providerPublicId;

    @Column(name = "title", length = 20)
    private String title;

    @Column(name = "given_name", length = 255)
    private String givenName;

    @Column(name = "family_name", length = 255)
    private String familyName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "nationality", length = 100)
    private String nationality;

    @Column(name = "national_id", length = 50)
    private String nationalId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "practice_number", length = 50)
    private String practiceNumber;

    @Column(name = "profession", length = 100)
    private String profession;

    @Column(name = "cadre", length = 100)
    private String cadre;

    @Column(name = "middle_name", length = 255)
    private String middleName;

    @Column(name = "previous_names", length = 255)
    private String previousNames;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_council_id")
    private CouncilEntity primaryCouncil;

    @Column(name = "employment_org_id")
    private Long employmentOrgId;

    @Column(name = "profile_photo_ref", length = 255)
    private String profilePhotoRef;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "lifecycle_status", length = 50)
    private String lifecycleStatus = "REGISTERED";

    @Column(name = "licence_status", length = 50)
    private String licenceStatus;

    @Column(name = "professional_standing_status", length = 50)
    private String professionalStandingStatus;

    @Column(name = "active_flag")
    private Boolean activeFlag = true;

    /**
     * Platform trust ladder position ({@code ProviderTrustLevel} name). Additive IATG
     * Wave-1 axis; only mutated through {@code ProviderTrustService} transitions.
     */
    @Column(name = "trust_level", length = 40)
    private String trustLevel;

    /** How this provider entered the platform ({@code OnboardingChannel} name). */
    @Column(name = "onboarding_channel", length = 20)
    private String onboardingChannel;

    /**
     * Honest registry answer ({@code ProviderRegistryStatus} name) — additive fifth
     * status axis; the legacy four axes (status, licenceStatus,
     * professionalStandingStatus, activeFlag) are untouched until Wave 2.
     */
    @Column(name = "registry_status", length = 40)
    private String registryStatus;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    /** How this provider row came into existence (BULK_PRELOAD, SELF_REGISTERED, COUNCIL_IMPORT). */
    @Column(name = "bootstrap_origin", length = 40)
    private String bootstrapOrigin;

    /** Groups a single bulk-preload run; null for self-registered profiles. */
    @Column(name = "preload_batch_id")
    private UUID preloadBatchId;

    /** When a PRELOADED profile was claimed by its real person. */
    @Column(name = "claimed_at")
    private Instant claimedAt;

    /** Impilo Health ID that claimed this preloaded profile. */
    @Column(name = "claimed_health_id")
    private UUID claimedHealthId;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (providerRef == null) {
            providerRef = UUID.randomUUID();
        }
        if (impiloHealthId == null) {
            impiloHealthId = providerRef;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Convenience methods

    public boolean isActive() {
        return !Boolean.FALSE.equals(activeFlag) && "ACTIVE".equals(status);
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getProviderRef() { return providerRef; }
    public void setProviderRef(UUID providerRef) { this.providerRef = providerRef; }

    public UUID getImpiloHealthId() { return impiloHealthId; }
    public void setImpiloHealthId(UUID impiloHealthId) { this.impiloHealthId = impiloHealthId; }

    public String getProviderPublicId() { return providerPublicId; }
    public void setProviderPublicId(String providerPublicId) { this.providerPublicId = providerPublicId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getGivenName() { return givenName; }
    public void setGivenName(String givenName) { this.givenName = givenName; }

    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }

    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPracticeNumber() { return practiceNumber; }
    public void setPracticeNumber(String practiceNumber) { this.practiceNumber = practiceNumber; }

    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }

    public String getCadre() { return cadre; }
    public void setCadre(String cadre) { this.cadre = cadre; }

    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    public String getPreviousNames() { return previousNames; }
    public void setPreviousNames(String previousNames) { this.previousNames = previousNames; }

    public CouncilEntity getPrimaryCouncil() { return primaryCouncil; }
    public void setPrimaryCouncil(CouncilEntity primaryCouncil) { this.primaryCouncil = primaryCouncil; }

    public Long getEmploymentOrgId() { return employmentOrgId; }
    public void setEmploymentOrgId(Long employmentOrgId) { this.employmentOrgId = employmentOrgId; }

    public String getProfilePhotoRef() { return profilePhotoRef; }
    public void setProfilePhotoRef(String profilePhotoRef) { this.profilePhotoRef = profilePhotoRef; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }

    public String getBootstrapOrigin() { return bootstrapOrigin; }
    public void setBootstrapOrigin(String bootstrapOrigin) { this.bootstrapOrigin = bootstrapOrigin; }

    public UUID getPreloadBatchId() { return preloadBatchId; }
    public void setPreloadBatchId(UUID preloadBatchId) { this.preloadBatchId = preloadBatchId; }

    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }

    public UUID getClaimedHealthId() { return claimedHealthId; }
    public void setClaimedHealthId(UUID claimedHealthId) { this.claimedHealthId = claimedHealthId; }

    public String getLicenceStatus() { return licenceStatus; }
    public void setLicenceStatus(String licenceStatus) { this.licenceStatus = licenceStatus; }

    public String getProfessionalStandingStatus() { return professionalStandingStatus; }
    public void setProfessionalStandingStatus(String professionalStandingStatus) { this.professionalStandingStatus = professionalStandingStatus; }

    public Boolean getActiveFlag() { return activeFlag; }
    public void setActiveFlag(Boolean activeFlag) { this.activeFlag = activeFlag; }

    public String getTrustLevel() { return trustLevel; }
    public void setTrustLevel(String trustLevel) { this.trustLevel = trustLevel; }

    public String getOnboardingChannel() { return onboardingChannel; }
    public void setOnboardingChannel(String onboardingChannel) { this.onboardingChannel = onboardingChannel; }

    public String getRegistryStatus() { return registryStatus; }
    public void setRegistryStatus(String registryStatus) { this.registryStatus = registryStatus; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
