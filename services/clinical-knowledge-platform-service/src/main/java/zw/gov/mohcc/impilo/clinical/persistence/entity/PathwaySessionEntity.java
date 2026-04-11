package zw.gov.mohcc.impilo.clinical.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "clinical", name = "pathway_sessions")
public class PathwaySessionEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "pathway_id", nullable = false)
    private UUID pathwayId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "patient_id")
    private String patientId;

    @Column(name = "encounter_id")
    private String encounterId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode stateJson;

    @Column(name = "current_step_order", nullable = false)
    private int currentStepOrder;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public UUID getId() {
        return id;
    }

    public UUID getPathwayId() {
        return pathwayId;
    }

    public int getCurrentStepOrder() {
        return currentStepOrder;
    }

    public JsonNode getStateJson() {
        return stateJson;
    }

    public String getStatus() {
        return status;
    }

    public void setPathwayId(UUID pathwayId) {
        this.pathwayId = pathwayId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public void setEncounterId(String encounterId) {
        this.encounterId = encounterId;
    }

    public void setStateJson(JsonNode stateJson) {
        this.stateJson = stateJson;
    }

    public void setCurrentStepOrder(int currentStepOrder) {
        this.currentStepOrder = currentStepOrder;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
