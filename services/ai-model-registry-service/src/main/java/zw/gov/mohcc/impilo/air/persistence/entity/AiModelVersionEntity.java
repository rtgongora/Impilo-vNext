package zw.gov.mohcc.impilo.air.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "model_versions", schema = "ai_registry")
public class AiModelVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false, unique = true, updatable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID versionId;

    @Column(name = "model_id", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID modelId;

    @Column(nullable = false, length = 32)
    private String version;

    @Column(name = "artifact_ref", length = 512)
    private String artifactRef;

    @Column(name = "config_json", columnDefinition = "jsonb")
    private String configJson = "{}";

    @Column(name = "approved_use_cases", columnDefinition = "jsonb")
    private String approvedUseCases = "[]";

    @Column(name = "prohibited_use_cases", columnDefinition = "jsonb")
    private String prohibitedUseCases = "[]";

    @Column(name = "drift_threshold", precision = 5, scale = 3)
    private BigDecimal driftThreshold;

    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (versionId == null) {
            versionId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (configJson == null) {
            configJson = "{}";
        }
        if (approvedUseCases == null) {
            approvedUseCases = "[]";
        }
        if (prohibitedUseCases == null) {
            prohibitedUseCases = "[]";
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
    }

    public UUID getModelId() {
        return modelId;
    }

    public void setModelId(UUID modelId) {
        this.modelId = modelId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getArtifactRef() {
        return artifactRef;
    }

    public void setArtifactRef(String artifactRef) {
        this.artifactRef = artifactRef;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public String getApprovedUseCases() {
        return approvedUseCases;
    }

    public void setApprovedUseCases(String approvedUseCases) {
        this.approvedUseCases = approvedUseCases;
    }

    public String getProhibitedUseCases() {
        return prohibitedUseCases;
    }

    public void setProhibitedUseCases(String prohibitedUseCases) {
        this.prohibitedUseCases = prohibitedUseCases;
    }

    public BigDecimal getDriftThreshold() {
        return driftThreshold;
    }

    public void setDriftThreshold(BigDecimal driftThreshold) {
        this.driftThreshold = driftThreshold;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
