package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One section of an application the applicant completes over time (NCZ-W1C).
 *
 * <p>Sections exist so a regulator can return work at the granularity of the mistake. Without them
 * the only available verdict is on the whole application, and an applicant who got one document
 * wrong starts again — which is the behaviour the brief is explicit about avoiding.</p>
 */
@Entity
@Table(name = "application_section", schema = "varapi")
public class ApplicationSectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "section_key", nullable = false, length = 64)
    private String sectionKey;

    @Column(nullable = false, length = 160)
    private String label;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo = 100;

    @Column(nullable = false, length = 24)
    private String state = "NOT_STARTED";

    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private com.fasterxml.jackson.databind.JsonNode content;

    @Column(name = "config_version_ref", length = 128)
    private String configVersionRef;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "returned_reason")
    private String returnedReason;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "returned_by", length = 128)
    private String returnedBy;

    @Column(name = "return_count", nullable = false)
    private int returnCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getSectionKey() { return sectionKey; }
    public void setSectionKey(String sectionKey) { this.sectionKey = sectionKey; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(int sequenceNo) { this.sequenceNo = sequenceNo; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public com.fasterxml.jackson.databind.JsonNode getContent() { return content; }
    public void setContent(com.fasterxml.jackson.databind.JsonNode content) { this.content = content; }
    public String getConfigVersionRef() { return configVersionRef; }
    public void setConfigVersionRef(String configVersionRef) { this.configVersionRef = configVersionRef; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getReturnedReason() { return returnedReason; }
    public void setReturnedReason(String returnedReason) { this.returnedReason = returnedReason; }
    public Instant getReturnedAt() { return returnedAt; }
    public void setReturnedAt(Instant returnedAt) { this.returnedAt = returnedAt; }
    public String getReturnedBy() { return returnedBy; }
    public void setReturnedBy(String returnedBy) { this.returnedBy = returnedBy; }
    public int getReturnCount() { return returnCount; }
    public void setReturnCount(int returnCount) { this.returnCount = returnCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
