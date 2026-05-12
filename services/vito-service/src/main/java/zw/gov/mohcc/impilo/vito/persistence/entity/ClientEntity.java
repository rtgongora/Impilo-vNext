package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.vito.core.ClientVerificationState;
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

    @Column(name = "middle_name")
    private String middleName;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "demographics", columnDefinition = "jsonb")
    private String demographics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contacts", columnDefinition = "jsonb")
    private String contacts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "address", columnDefinition = "jsonb")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IdentityStatus status = IdentityStatus.PROVISIONAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private ClientVerificationState verificationStatus = ClientVerificationState.UNVERIFIED;

    @Column(name = "identity_assurance_level", nullable = false)
    private int identityAssuranceLevel;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;

    @Column(name = "deceased_flag", nullable = false)
    private boolean deceasedFlag;

    @Column(name = "golden_record_flag", nullable = false)
    private boolean goldenRecordFlag = true;

    @Column(name = "merged_into_crid")
    private UUID mergedIntoCrid;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lifecycle_metadata", columnDefinition = "jsonb")
    private String lifecycleMetadata;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @Column(name = "source_system_patient_id", length = 255)
    private String sourceSystemPatientId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (healthId == null) {
            healthId = UUID.randomUUID();
        }
        if (crid == null) {
            crid = UUID.randomUUID();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getHealthId() { return healthId; }
    public void setHealthId(UUID healthId) { this.healthId = healthId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getGivenName() { return givenName; }
    public void setGivenName(String givenName) { this.givenName = givenName; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
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
    public ClientVerificationState getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(ClientVerificationState verificationStatus) { this.verificationStatus = verificationStatus; }
    public int getIdentityAssuranceLevel() { return identityAssuranceLevel; }
    public void setIdentityAssuranceLevel(int identityAssuranceLevel) { this.identityAssuranceLevel = identityAssuranceLevel; }
    public boolean isActiveFlag() { return activeFlag; }
    public void setActiveFlag(boolean activeFlag) { this.activeFlag = activeFlag; }
    public boolean isDeceasedFlag() { return deceasedFlag; }
    public void setDeceasedFlag(boolean deceasedFlag) { this.deceasedFlag = deceasedFlag; }
    public boolean isGoldenRecordFlag() { return goldenRecordFlag; }
    public void setGoldenRecordFlag(boolean goldenRecordFlag) { this.goldenRecordFlag = goldenRecordFlag; }
    public UUID getMergedIntoCrid() { return mergedIntoCrid; }
    public void setMergedIntoCrid(UUID mergedIntoCrid) { this.mergedIntoCrid = mergedIntoCrid; }
    public String getLifecycleMetadata() { return lifecycleMetadata; }
    public void setLifecycleMetadata(String lifecycleMetadata) { this.lifecycleMetadata = lifecycleMetadata; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getSourceSystemPatientId() { return sourceSystemPatientId; }
    public void setSourceSystemPatientId(String sourceSystemPatientId) { this.sourceSystemPatientId = sourceSystemPatientId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
