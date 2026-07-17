package zw.gov.mohcc.impilo.participation.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A citizen's enrolment into a testing cohort (table
 * {@code participation.testing_enrollment}).
 */
@Entity
@Table(name = "testing_enrollment", schema = "participation")
public class TestingEnrollmentEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "cohort_id", nullable = false)
    private UUID cohortId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "contributor_ref", length = 128)
    private String contributorRef;

    @Column(name = "contact", length = 255)
    private String contact;

    @Column(name = "segment", length = 48)
    private String segment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCohortId() { return cohortId; }
    public void setCohortId(UUID cohortId) { this.cohortId = cohortId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getContributorRef() { return contributorRef; }
    public void setContributorRef(String contributorRef) { this.contributorRef = contributorRef; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getSegment() { return segment; }
    public void setSegment(String segment) { this.segment = segment; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
