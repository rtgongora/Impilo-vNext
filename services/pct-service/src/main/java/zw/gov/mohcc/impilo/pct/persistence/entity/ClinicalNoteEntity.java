package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pct_clinical_notes")
public class ClinicalNoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false, length = 128)
    private String patientId;

    @Column(name = "subject_cpid")
    private UUID subjectCpid;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "note_type", nullable = false, length = 64)
    private String noteType = "GENERAL";

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "subjective", columnDefinition = "TEXT")
    private String subjective;

    @Column(name = "objective", columnDefinition = "TEXT")
    private String objective;

    @Column(name = "assessment", columnDefinition = "TEXT")
    private String assessment;

    @Column(name = "plan", columnDefinition = "TEXT")
    private String plan;

    @Column(name = "author_id", nullable = false)
    private String authorId;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "patient_share_grant_id")
    private Long patientShareGrantId;

    @Column(name = "vito_contribution_id")
    private Long vitoContributionId;

    @Column(name = "temporary_provider_public_id", length = 26)
    private String temporaryProviderPublicId;

    @Column(name = "trust_level_at_authoring", length = 64)
    private String trustLevelAtAuthoring;

    @Column(name = "share_correlation_id", length = 64)
    private String shareCorrelationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provenance", columnDefinition = "jsonb")
    private String provenance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public UUID getSubjectCpid() {
        return subjectCpid;
    }

    public void setSubjectCpid(UUID subjectCpid) {
        this.subjectCpid = subjectCpid;
    }

    public UUID getEncounterId() {
        return encounterId;
    }

    public void setEncounterId(UUID encounterId) {
        this.encounterId = encounterId;
    }

    public String getNoteType() {
        return noteType;
    }

    public void setNoteType(String noteType) {
        this.noteType = noteType;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getSubjective() {
        return subjective;
    }

    public void setSubjective(String subjective) {
        this.subjective = subjective;
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public String getAssessment() {
        return assessment;
    }

    public void setAssessment(String assessment) {
        this.assessment = assessment;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public Long getPatientShareGrantId() {
        return patientShareGrantId;
    }

    public void setPatientShareGrantId(Long patientShareGrantId) {
        this.patientShareGrantId = patientShareGrantId;
    }

    public Long getVitoContributionId() {
        return vitoContributionId;
    }

    public void setVitoContributionId(Long vitoContributionId) {
        this.vitoContributionId = vitoContributionId;
    }

    public String getTemporaryProviderPublicId() {
        return temporaryProviderPublicId;
    }

    public void setTemporaryProviderPublicId(String temporaryProviderPublicId) {
        this.temporaryProviderPublicId = temporaryProviderPublicId;
    }

    public String getTrustLevelAtAuthoring() {
        return trustLevelAtAuthoring;
    }

    public void setTrustLevelAtAuthoring(String trustLevelAtAuthoring) {
        this.trustLevelAtAuthoring = trustLevelAtAuthoring;
    }

    public String getShareCorrelationId() {
        return shareCorrelationId;
    }

    public void setShareCorrelationId(String shareCorrelationId) {
        this.shareCorrelationId = shareCorrelationId;
    }

    public String getProvenance() {
        return provenance;
    }

    public void setProvenance(String provenance) {
        this.provenance = provenance;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
