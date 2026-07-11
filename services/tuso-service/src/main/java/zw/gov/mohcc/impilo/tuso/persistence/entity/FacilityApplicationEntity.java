package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "facility_application", schema = "tuso")
public class FacilityApplicationEntity {

    @Id
    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id")
    private FacilityEntity facility;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_type", nullable = false, length = 64)
    private FacilityApplicationType applicationType;

    @Column(name = "pathway", length = 64)
    private String pathway;

    @Column(name = "applicant_name", nullable = false, length = 255)
    private String applicantName;

    @Column(name = "applicant_email", length = 255)
    private String applicantEmail;

    @Column(name = "applicant_phone", length = 64)
    private String applicantPhone;

    @Column(name = "applicant_organisation", length = 255)
    private String applicantOrganisation;

    @Column(name = "submission_date")
    private Instant submissionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_workflow_state", nullable = false, length = 64)
    private FacilityApplicationState currentWorkflowState = FacilityApplicationState.DRAFT;

    @Column(name = "priority", nullable = false, length = 32)
    private String priority = "NORMAL";

    @Column(name = "fee_state", nullable = false, length = 32)
    private String feeState = "PENDING";

    @Column(name = "committee_state", nullable = false, length = 64)
    private String committeeState = "NOT_STARTED";

    @Column(name = "final_decision", length = 64)
    private String finalDecision;

    @Column(name = "decision_date")
    private Instant decisionDate;

    @Column(name = "institution_file_number", length = 64)
    private String institutionFileNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_changes", columnDefinition = "jsonb")
    private Map<String, Object> requestedChanges;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "workflow_notes", columnDefinition = "text")
    private String workflowNotes;

    @Column(name = "catalogue_code", length = 64)
    private String catalogueCode;

    @Column(name = "facility_unit_id")
    private Long facilityUnitId;

    @Column(name = "premises_id")
    private UUID premisesId;

    @Column(name = "fee_reference", length = 128)
    private String feeReference;

    @Column(name = "payment_reference", length = 128)
    private String paymentReference;

    @Column(name = "application_number", length = 64)
    private String applicationNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (applicationId == null) {
            applicationId = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }
    public FacilityApplicationType getApplicationType() { return applicationType; }
    public void setApplicationType(FacilityApplicationType applicationType) { this.applicationType = applicationType; }
    public String getPathway() { return pathway; }
    public void setPathway(String pathway) { this.pathway = pathway; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getApplicantEmail() { return applicantEmail; }
    public void setApplicantEmail(String applicantEmail) { this.applicantEmail = applicantEmail; }
    public String getApplicantPhone() { return applicantPhone; }
    public void setApplicantPhone(String applicantPhone) { this.applicantPhone = applicantPhone; }
    public String getApplicantOrganisation() { return applicantOrganisation; }
    public void setApplicantOrganisation(String applicantOrganisation) { this.applicantOrganisation = applicantOrganisation; }
    public Instant getSubmissionDate() { return submissionDate; }
    public void setSubmissionDate(Instant submissionDate) { this.submissionDate = submissionDate; }
    public FacilityApplicationState getCurrentWorkflowState() { return currentWorkflowState; }
    public void setCurrentWorkflowState(FacilityApplicationState currentWorkflowState) { this.currentWorkflowState = currentWorkflowState; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getFeeState() { return feeState; }
    public void setFeeState(String feeState) { this.feeState = feeState; }
    public String getCommitteeState() { return committeeState; }
    public void setCommitteeState(String committeeState) { this.committeeState = committeeState; }
    public String getFinalDecision() { return finalDecision; }
    public void setFinalDecision(String finalDecision) { this.finalDecision = finalDecision; }
    public Instant getDecisionDate() { return decisionDate; }
    public void setDecisionDate(Instant decisionDate) { this.decisionDate = decisionDate; }
    public String getInstitutionFileNumber() { return institutionFileNumber; }
    public void setInstitutionFileNumber(String institutionFileNumber) { this.institutionFileNumber = institutionFileNumber; }
    public Map<String, Object> getRequestedChanges() { return requestedChanges; }
    public void setRequestedChanges(Map<String, Object> requestedChanges) { this.requestedChanges = requestedChanges; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public String getWorkflowNotes() { return workflowNotes; }
    public void setWorkflowNotes(String workflowNotes) { this.workflowNotes = workflowNotes; }
    public String getCatalogueCode() { return catalogueCode; }
    public void setCatalogueCode(String catalogueCode) { this.catalogueCode = catalogueCode; }
    public Long getFacilityUnitId() { return facilityUnitId; }
    public void setFacilityUnitId(Long facilityUnitId) { this.facilityUnitId = facilityUnitId; }
    public UUID getPremisesId() { return premisesId; }
    public void setPremisesId(UUID premisesId) { this.premisesId = premisesId; }
    public String getFeeReference() { return feeReference; }
    public void setFeeReference(String feeReference) { this.feeReference = feeReference; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public String getApplicationNumber() { return applicationNumber; }
    public void setApplicationNumber(String applicationNumber) { this.applicationNumber = applicationNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
