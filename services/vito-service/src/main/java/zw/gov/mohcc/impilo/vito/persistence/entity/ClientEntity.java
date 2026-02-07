package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.vito.core.IdentityStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "client", schema = "vito")
public class ClientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "health_id", nullable = false, unique = true)
    private UUID healthId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "given_name")
    private String givenName;

    @Column(name = "family_name")
    private String familyName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "sex")
    private String sex;

    @Column(name = "phone_hash")
    private String phoneHash;

    @Column(name = "crid", nullable = false, unique = true)
    private UUID crid;

    @Column(name = "impilo_id")
    private String impiloId;

    @Column(name = "demographics", columnDefinition = "jsonb")
    private String demographics;

    @Column(name = "contacts", columnDefinition = "jsonb")
    private String contacts;

    @Column(name = "address", columnDefinition = "jsonb")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IdentityStatus status = IdentityStatus.PROVISIONAL;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (healthId == null) healthId = UUID.randomUUID();
        if (crid == null) crid = UUID.randomUUID();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getHealthId() { return healthId; }
    public void setHealthId(UUID healthId) { this.healthId = healthId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getGivenName() { return givenName; }
    public void setGivenName(String givenName) { this.givenName = givenName; }
    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }
    public String getPhoneHash() { return phoneHash; }
    public void setPhoneHash(String phoneHash) { this.phoneHash = phoneHash; }
    public UUID getCrid() { return crid; }
    public void setCrid(UUID crid) { this.crid = crid; }
    public String getImpiloId() { return impiloId; }
    public void setImpiloId(String impiloId) { this.impiloId = impiloId; }
    public String getDemographics() { return demographics; }
    public void setDemographics(String demographics) { this.demographics = demographics; }
    public String getContacts() { return contacts; }
    public void setContacts(String contacts) { this.contacts = contacts; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public IdentityStatus getStatus() { return status; }
    public void setStatus(IdentityStatus status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
