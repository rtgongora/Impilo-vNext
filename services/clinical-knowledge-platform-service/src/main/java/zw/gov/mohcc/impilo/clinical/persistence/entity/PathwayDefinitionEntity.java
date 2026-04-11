package zw.gov.mohcc.impilo.clinical.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(schema = "clinical", name = "pathway_definitions")
public class PathwayDefinitionEntity {

    @Id
    private UUID id;

    @Column(name = "pathway_name", nullable = false)
    private String pathwayName;

    @Column(name = "condition_code", nullable = false)
    private String conditionCode;

    private Integer version;
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "pathway_id", insertable = false, updatable = false)
    @OrderBy("stepOrder ASC")
    private List<PathwayStepEntity> steps = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public String getPathwayName() {
        return pathwayName;
    }

    public String getConditionCode() {
        return conditionCode;
    }

    public Integer getVersion() {
        return version;
    }

    public String getStatus() {
        return status;
    }

    public List<PathwayStepEntity> getSteps() {
        return steps;
    }
}
