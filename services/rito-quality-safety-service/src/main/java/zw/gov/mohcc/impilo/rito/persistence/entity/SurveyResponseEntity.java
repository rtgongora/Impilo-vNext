package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rit_survey_response", schema = "rito")
public class SurveyResponseEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "survey_id", nullable = false)
    private UUID surveyId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "respondent_actor_id", length = 128)
    private String respondentActorId;

    @Column(name = "respondent_actor_type", length = 48)
    private String respondentActorType;

    @Column(name = "anonymous", nullable = false)
    private Boolean anonymous;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "visit_ref", length = 255)
    private String visitRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers_json", nullable = false, columnDefinition = "jsonb")
    private String answersJson;

    @Column(name = "score")
    private BigDecimal score;

    @Column(name = "nps_category", length = 16)
    private String npsCategory;

    @Column(name = "free_text", columnDefinition = "TEXT")
    private String freeText;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (submittedAt == null) {
            submittedAt = now;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSurveyId() { return surveyId; }
    public void setSurveyId(UUID surveyId) { this.surveyId = surveyId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getRespondentActorId() { return respondentActorId; }
    public void setRespondentActorId(String respondentActorId) { this.respondentActorId = respondentActorId; }
    public String getRespondentActorType() { return respondentActorType; }
    public void setRespondentActorType(String respondentActorType) { this.respondentActorType = respondentActorType; }
    public Boolean getAnonymous() { return anonymous; }
    public void setAnonymous(Boolean anonymous) { this.anonymous = anonymous; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getVisitRef() { return visitRef; }
    public void setVisitRef(String visitRef) { this.visitRef = visitRef; }
    public String getAnswersJson() { return answersJson; }
    public void setAnswersJson(String answersJson) { this.answersJson = answersJson; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getNpsCategory() { return npsCategory; }
    public void setNpsCategory(String npsCategory) { this.npsCategory = npsCategory; }
    public String getFreeText() { return freeText; }
    public void setFreeText(String freeText) { this.freeText = freeText; }
    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
