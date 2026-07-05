package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "wgv_hsc_employment")
public class HscEmploymentEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_worker_id", nullable = false)
    private String providerWorkerId;

    /**
     * Health Service Commission EC number (Channel B public-workforce matching key).
     * Deliberately NOT unique — duplicate EC numbers are an honest CONFLICT signal
     * surfaced by the employment match API.
     */
    @Column(name = "ec_number", length = 32)
    private String ecNumber;

    @Column(name = "linked_health_id")
    private String linkedHealthId;

    @Column(name = "employer_organisation_id")
    private UUID employerOrganisationId;

    @Column(name = "employment_status", nullable = false, length = 64)
    private String employmentStatus;

    @Column(name = "post_id", length = 128)
    private String postId;

    @Column(name = "post_title")
    private String postTitle;

    @Column(name = "grade", length = 64)
    private String grade;

    @Column(name = "cadre", length = 128)
    private String cadre;

    @Column(name = "establishment_unit")
    private String establishmentUnit;

    @Column(name = "current_posting_province", length = 128)
    private String currentPostingProvince;

    @Column(name = "current_posting_district", length = 128)
    private String currentPostingDistrict;

    @Column(name = "current_posting_facility", length = 128)
    private String currentPostingFacility;

    @Column(name = "current_posting_department", length = 128)
    private String currentPostingDepartment;

    @Column(name = "appointment_date")
    private LocalDate appointmentDate;

    @Column(name = "transfer_status", length = 64)
    private String transferStatus;

    @Column(name = "promotion_status", length = 64)
    private String promotionStatus;

    @Column(name = "disciplinary_employment_status", length = 64)
    private String disciplinaryEmploymentStatus;

    @Column(name = "verification_status", length = 64)
    private String verificationStatus;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public HscEmploymentEntity() {}

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getProviderWorkerId() { return providerWorkerId; }
    public void setProviderWorkerId(String providerWorkerId) { this.providerWorkerId = providerWorkerId; touch(); }
    public String getEcNumber() { return ecNumber; }
    public void setEcNumber(String ecNumber) { this.ecNumber = ecNumber; touch(); }
    public String getLinkedHealthId() { return linkedHealthId; }
    public void setLinkedHealthId(String linkedHealthId) { this.linkedHealthId = linkedHealthId; touch(); }
    public UUID getEmployerOrganisationId() { return employerOrganisationId; }
    public void setEmployerOrganisationId(UUID employerOrganisationId) { this.employerOrganisationId = employerOrganisationId; touch(); }
    public String getEmploymentStatus() { return employmentStatus; }
    public void setEmploymentStatus(String employmentStatus) { this.employmentStatus = employmentStatus; touch(); }
    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; touch(); }
    public String getPostTitle() { return postTitle; }
    public void setPostTitle(String postTitle) { this.postTitle = postTitle; touch(); }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; touch(); }
    public String getCadre() { return cadre; }
    public void setCadre(String cadre) { this.cadre = cadre; touch(); }
    public String getEstablishmentUnit() { return establishmentUnit; }
    public void setEstablishmentUnit(String establishmentUnit) { this.establishmentUnit = establishmentUnit; touch(); }
    public String getCurrentPostingProvince() { return currentPostingProvince; }
    public void setCurrentPostingProvince(String currentPostingProvince) { this.currentPostingProvince = currentPostingProvince; touch(); }
    public String getCurrentPostingDistrict() { return currentPostingDistrict; }
    public void setCurrentPostingDistrict(String currentPostingDistrict) { this.currentPostingDistrict = currentPostingDistrict; touch(); }
    public String getCurrentPostingFacility() { return currentPostingFacility; }
    public void setCurrentPostingFacility(String currentPostingFacility) { this.currentPostingFacility = currentPostingFacility; touch(); }
    public String getCurrentPostingDepartment() { return currentPostingDepartment; }
    public void setCurrentPostingDepartment(String currentPostingDepartment) { this.currentPostingDepartment = currentPostingDepartment; touch(); }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; touch(); }
    public String getTransferStatus() { return transferStatus; }
    public void setTransferStatus(String transferStatus) { this.transferStatus = transferStatus; touch(); }
    public String getPromotionStatus() { return promotionStatus; }
    public void setPromotionStatus(String promotionStatus) { this.promotionStatus = promotionStatus; touch(); }
    public String getDisciplinaryEmploymentStatus() { return disciplinaryEmploymentStatus; }
    public void setDisciplinaryEmploymentStatus(String disciplinaryEmploymentStatus) { this.disciplinaryEmploymentStatus = disciplinaryEmploymentStatus; touch(); }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; touch(); }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; touch(); }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; touch(); }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; touch(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void init(UUID tenantId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
