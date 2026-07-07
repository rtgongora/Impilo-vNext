package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A single answered question within an assessment (one row per question_code per assessment). */
@Entity
@Table(name = "simba_assessment_response", schema = "simba")
public class AssessmentResponseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "response_id", nullable = false, unique = true)
    private UUID responseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "section", nullable = false, length = 24)
    private String section;

    @Column(name = "question_code", nullable = false, length = 64)
    private String questionCode;

    @Column(name = "value_text", length = 255)
    private String valueText;

    @Column(name = "value_numeric")
    private BigDecimal valueNumeric;

    @Column(name = "unit", length = 24)
    private String unit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (responseId == null) {
            responseId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getResponseId() { return responseId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getAssessmentId() { return assessmentId; }
    public void setAssessmentId(UUID assessmentId) { this.assessmentId = assessmentId; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getQuestionCode() { return questionCode; }
    public void setQuestionCode(String questionCode) { this.questionCode = questionCode; }
    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText; }
    public BigDecimal getValueNumeric() { return valueNumeric; }
    public void setValueNumeric(BigDecimal valueNumeric) { this.valueNumeric = valueNumeric; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
