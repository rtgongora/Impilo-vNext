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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_council_id")
    private CouncilEntity primaryCouncil;

    @Column(name = "employment_org_id")
    private Long employmentOrgId;

    @Column(name = "profile_photo_ref", length = 255)
    private String profilePhotoRef;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

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

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (providerRef == null) providerRef = UUID.randomUUID();
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

    public UUID getProviderRef() { return providerRef; }
    public void setProviderRef(UUID providerRef) { this.providerRef = providerRef; }

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

    public CouncilEntity getPrimaryCouncil() { return primaryCouncil; }
    public void setPrimaryCouncil(CouncilEntity primaryCouncil) { this.primaryCouncil = primaryCouncil; }

    public Long getEmploymentOrgId() { return employmentOrgId; }
    public void setEmploymentOrgId(Long employmentOrgId) { this.employmentOrgId = employmentOrgId; }

    public String getProfilePhotoRef() { return profilePhotoRef; }
    public void setProfilePhotoRef(String profilePhotoRef) { this.profilePhotoRef = profilePhotoRef; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
