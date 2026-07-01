package zw.gov.mohcc.impilo.ubomi.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "death_notification", schema = "ubomi")
public class DeathNotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "notification_number", nullable = false)
    private String notificationNumber;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "deceased_cpid", nullable = false)
    private String deceasedCpid;

    @Column(name = "date_of_death", nullable = false)
    private LocalDate dateOfDeath;

    @Column(name = "time_of_death")
    private LocalTime timeOfDeath;

    @Column(name = "place_of_death_type", nullable = false)
    private String placeOfDeathType;

    @Column(name = "manner_of_death")
    private String mannerOfDeath;

    @Column(name = "immediate_cause")
    private String immediateCause;

    @Column(name = "underlying_cause")
    private String underlyingCause;

    @Column(name = "contributing_conditions")
    private String contributingConditions;

    @Column(name = "certifying_practitioner")
    private String certifyingPractitioner;

    @Column(name = "certifier_role")
    private String certifierRole;

    @Column(name = "certified_at")
    private OffsetDateTime certifiedAt;

    @Column(name = "notified_by_actor", nullable = false)
    private String notifiedByActor;

    @Column(name = "civil_reg_number")
    private String civilRegNumber;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "registered_at")
    private OffsetDateTime registeredAt;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "pct_case_ref")
    private String pctCaseRef;

    /** Certainty class of the cause — keeps verbal-autopsy/field-probable distinct from certified. */
    @Column(name = "cause_of_death_basis")
    private String causeOfDeathBasis;

    /** Origin of the death (facility / brought-in-dead / community / field / etc.). */
    @Column(name = "source_context")
    private String sourceContext;

    /** Where the body went; a community death need not enter facility mortuary custody. */
    @Column(name = "body_disposition_context")
    private String bodyDispositionContext;

    @Column(name = "package_ready", nullable = false)
    private boolean packageReady = false;

    @Column(name = "package_validated_at")
    private OffsetDateTime packageValidatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (status == null) status = "SUBMITTED";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getNotificationNumber() { return notificationNumber; }
    public void setNotificationNumber(String notificationNumber) { this.notificationNumber = notificationNumber; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getDeceasedCpid() { return deceasedCpid; }
    public void setDeceasedCpid(String deceasedCpid) { this.deceasedCpid = deceasedCpid; }
    public LocalDate getDateOfDeath() { return dateOfDeath; }
    public void setDateOfDeath(LocalDate dateOfDeath) { this.dateOfDeath = dateOfDeath; }
    public LocalTime getTimeOfDeath() { return timeOfDeath; }
    public void setTimeOfDeath(LocalTime timeOfDeath) { this.timeOfDeath = timeOfDeath; }
    public String getPlaceOfDeathType() { return placeOfDeathType; }
    public void setPlaceOfDeathType(String placeOfDeathType) { this.placeOfDeathType = placeOfDeathType; }
    public String getMannerOfDeath() { return mannerOfDeath; }
    public void setMannerOfDeath(String mannerOfDeath) { this.mannerOfDeath = mannerOfDeath; }
    public String getImmediateCause() { return immediateCause; }
    public void setImmediateCause(String immediateCause) { this.immediateCause = immediateCause; }
    public String getUnderlyingCause() { return underlyingCause; }
    public void setUnderlyingCause(String underlyingCause) { this.underlyingCause = underlyingCause; }
    public String getContributingConditions() { return contributingConditions; }
    public void setContributingConditions(String contributingConditions) { this.contributingConditions = contributingConditions; }
    public String getCertifyingPractitioner() { return certifyingPractitioner; }
    public void setCertifyingPractitioner(String certifyingPractitioner) { this.certifyingPractitioner = certifyingPractitioner; }
    public String getCertifierRole() { return certifierRole; }
    public void setCertifierRole(String certifierRole) { this.certifierRole = certifierRole; }
    public OffsetDateTime getCertifiedAt() { return certifiedAt; }
    public void setCertifiedAt(OffsetDateTime certifiedAt) { this.certifiedAt = certifiedAt; }
    public String getNotifiedByActor() { return notifiedByActor; }
    public void setNotifiedByActor(String notifiedByActor) { this.notifiedByActor = notifiedByActor; }
    public String getCivilRegNumber() { return civilRegNumber; }
    public void setCivilRegNumber(String civilRegNumber) { this.civilRegNumber = civilRegNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public OffsetDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(OffsetDateTime registeredAt) { this.registeredAt = registeredAt; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public String getPctCaseRef() { return pctCaseRef; }
    public void setPctCaseRef(String pctCaseRef) { this.pctCaseRef = pctCaseRef; }
    public String getCauseOfDeathBasis() { return causeOfDeathBasis; }
    public void setCauseOfDeathBasis(String causeOfDeathBasis) { this.causeOfDeathBasis = causeOfDeathBasis; }
    public String getSourceContext() { return sourceContext; }
    public void setSourceContext(String sourceContext) { this.sourceContext = sourceContext; }
    public String getBodyDispositionContext() { return bodyDispositionContext; }
    public void setBodyDispositionContext(String bodyDispositionContext) { this.bodyDispositionContext = bodyDispositionContext; }
    public boolean isPackageReady() { return packageReady; }
    public void setPackageReady(boolean packageReady) { this.packageReady = packageReady; }
    public OffsetDateTime getPackageValidatedAt() { return packageValidatedAt; }
    public void setPackageValidatedAt(OffsetDateTime packageValidatedAt) { this.packageValidatedAt = packageValidatedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
