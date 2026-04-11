package zw.gov.mohcc.impilo.clinical.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(schema = "clinical", name = "pathway_steps")
public class PathwayStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pathway_id", nullable = false)
    private UUID pathwayId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "step_type", nullable = false)
    private String stepType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_capture_schema", columnDefinition = "jsonb")
    private JsonNode dataCaptureSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "branch_logic", columnDefinition = "jsonb")
    private JsonNode branchLogic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendation_logic", columnDefinition = "jsonb")
    private JsonNode recommendationLogic;

    public UUID getId() {
        return id;
    }

    public Integer getStepOrder() {
        return stepOrder;
    }

    public String getStepType() {
        return stepType;
    }

    public String getPrompt() {
        return prompt;
    }

    public JsonNode getDataCaptureSchema() {
        return dataCaptureSchema;
    }

    public JsonNode getBranchLogic() {
        return branchLogic;
    }

    public JsonNode getRecommendationLogic() {
        return recommendationLogic;
    }
}
