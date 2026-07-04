package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fundo_cpd_candidates", schema = "varapi")
public class FundoCpdCandidateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "council_id")
    private CouncilEntity council;

    @Column(name = "course_id", nullable = false, length = 120)
    private String courseId;

    @Column(name = "course_name", length = 512)
    private String courseName;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "credits_suggested", nullable = false)
    private int creditsSuggested;

    @Column(name = "verification_state", nullable = false, length = 30)
    private String verificationState = "PENDING";

    @Column(name = "external_ref", nullable = false, length = 255)
    private String externalRef;

    @Column(name = "linked_cpd_event_id")
    private Long linkedCpdEventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "JSONB")
    private String rawPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public ProviderEntity getProvider() { return provider; }
    public void setProvider(ProviderEntity provider) { this.provider = provider; }
    public CouncilEntity getCouncil() { return council; }
    public void setCouncil(CouncilEntity council) { this.council = council; }
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public int getCreditsSuggested() { return creditsSuggested; }
    public void setCreditsSuggested(int creditsSuggested) { this.creditsSuggested = creditsSuggested; }
    public String getVerificationState() { return verificationState; }
    public void setVerificationState(String verificationState) { this.verificationState = verificationState; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public Long getLinkedCpdEventId() { return linkedCpdEventId; }
    public void setLinkedCpdEventId(Long linkedCpdEventId) { this.linkedCpdEventId = linkedCpdEventId; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
