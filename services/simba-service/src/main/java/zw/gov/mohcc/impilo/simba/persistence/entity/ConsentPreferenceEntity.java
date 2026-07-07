package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Person's wellness consent preferences. Read by {@code WellnessAccessGuard} to gate cross-person,
 * caregiver, provider, programme, and sensitive-category access. One row per (tenant, person).
 */
@Entity
@Table(name = "simba_consent_preference", schema = "simba")
public class ConsentPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consent_id", nullable = false, unique = true)
    private UUID consentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "person_cpid", nullable = false, length = 128)
    private String personCpid;

    @Column(name = "caregiver_involvement", nullable = false)
    private boolean caregiverInvolvement = false;

    @Column(name = "provider_involvement", nullable = false)
    private boolean providerInvolvement = true;

    @Column(name = "programme_involvement", nullable = false)
    private boolean programmeInvolvement = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sensitive_categories", columnDefinition = "jsonb")
    private String sensitiveCategories = "[]";

    @Column(name = "data_sharing_scope", nullable = false, length = 24)
    private String dataSharingScope = "CARE_TEAM";

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (consentId == null) {
            consentId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getConsentId() { return consentId; }
    public void setConsentId(UUID consentId) { this.consentId = consentId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPersonCpid() { return personCpid; }
    public void setPersonCpid(String personCpid) { this.personCpid = personCpid; }
    public boolean isCaregiverInvolvement() { return caregiverInvolvement; }
    public void setCaregiverInvolvement(boolean v) { this.caregiverInvolvement = v; }
    public boolean isProviderInvolvement() { return providerInvolvement; }
    public void setProviderInvolvement(boolean v) { this.providerInvolvement = v; }
    public boolean isProgrammeInvolvement() { return programmeInvolvement; }
    public void setProgrammeInvolvement(boolean v) { this.programmeInvolvement = v; }
    public String getSensitiveCategories() { return sensitiveCategories; }
    public void setSensitiveCategories(String v) { this.sensitiveCategories = v; }
    public String getDataSharingScope() { return dataSharingScope; }
    public void setDataSharingScope(String v) { this.dataSharingScope = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
