package zw.gov.mohcc.impilo.surv.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A person traced as having been exposed to a case.
 *
 * <p>Deliberately not a patient reference. Contact tracing happens before identity is established —
 * a name and a phone number taken at a household visit is the normal case, and requiring a CPID
 * would make the feature unusable exactly when it matters most. {@code contactCpid} is filled in
 * later, if and when the person is identified.
 *
 * <p>{@code traceStatus} is tracing state, never clinical state. A contact who develops symptoms
 * becomes a case in their own right; this row never carries a diagnosis.
 */
@Entity
@Table(name = "case_contacts", schema = "surv")
public class CaseContactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "contact_cpid")
    private String contactCpid;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "relationship")
    private String relationship;

    @Column(name = "exposure_date")
    private LocalDate exposureDate;

    @Column(name = "trace_status", nullable = false)
    private String traceStatus = "PENDING";

    @Column(name = "last_contacted_at")
    private OffsetDateTime lastContactedAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getContactCpid() { return contactCpid; }
    public void setContactCpid(String contactCpid) { this.contactCpid = contactCpid; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }
    public LocalDate getExposureDate() { return exposureDate; }
    public void setExposureDate(LocalDate exposureDate) { this.exposureDate = exposureDate; }
    public String getTraceStatus() { return traceStatus; }
    public void setTraceStatus(String traceStatus) { this.traceStatus = traceStatus; }
    public OffsetDateTime getLastContactedAt() { return lastContactedAt; }
    public void setLastContactedAt(OffsetDateTime lastContactedAt) { this.lastContactedAt = lastContactedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
