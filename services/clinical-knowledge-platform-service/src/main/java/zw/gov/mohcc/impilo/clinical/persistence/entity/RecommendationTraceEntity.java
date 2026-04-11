package zw.gov.mohcc.impilo.clinical.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "clinical", name = "recommendation_traces")
public class RecommendationTraceEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String role;

    @Column(name = "patient_id")
    private String patientId;

    @Column(name = "encounter_id")
    private String encounterId;

    @Column(name = "request_type", nullable = false)
    private String requestType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_context_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode inputContextJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendations_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode recommendationsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_refs_json", columnDefinition = "jsonb")
    private JsonNode sourceRefsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules_triggered_json", columnDefinition = "jsonb")
    private JsonNode rulesTriggeredJson;

    @Column(name = "knowledge_version")
    private String knowledgeVersion;

    @Column(name = "source_version")
    private String sourceVersion;

    @Column(name = "support_mode", nullable = false)
    private String supportMode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSupportMode() {
        return supportMode;
    }

    public String getRequestType() {
        return requestType;
    }

    public String getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public void setEncounterId(String encounterId) {
        this.encounterId = encounterId;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public void setInputContextJson(JsonNode inputContextJson) {
        this.inputContextJson = inputContextJson;
    }

    public void setRecommendationsJson(JsonNode recommendationsJson) {
        this.recommendationsJson = recommendationsJson;
    }

    public void setSourceRefsJson(JsonNode sourceRefsJson) {
        this.sourceRefsJson = sourceRefsJson;
    }

    public void setRulesTriggeredJson(JsonNode rulesTriggeredJson) {
        this.rulesTriggeredJson = rulesTriggeredJson;
    }

    public void setKnowledgeVersion(String knowledgeVersion) {
        this.knowledgeVersion = knowledgeVersion;
    }

    public void setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
    }

    public void setSupportMode(String supportMode) {
        this.supportMode = supportMode;
    }

    public JsonNode getInputContextJson() {
        return inputContextJson;
    }

    public JsonNode getRecommendationsJson() {
        return recommendationsJson;
    }

    public JsonNode getRulesTriggeredJson() {
        return rulesTriggeredJson;
    }

    public String getKnowledgeVersion() {
        return knowledgeVersion;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }
}
