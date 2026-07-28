package zw.gov.mohcc.impilo.procedures.persistence.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * One state on the sedation continuum (pipeline §10). Requirements-side only — the executing
 * service's own anaesthesia record captures the actual encounter (assessment, fasting,
 * medicines, complications); this entity holds what a depth REQUIRES: monitoring, provider
 * competence, and — the standard's governing rule — the rescue capability of the next level
 * up, because sedation depth is not perfectly controllable.
 */
@Entity
@Table(name = "sedation_level", schema = "procedures")
public class SedationLevelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "level_code", nullable = false, length = 32)
    private String levelCode;

    @Column(name = "level_label", nullable = false, length = 96)
    private String levelLabel;

    @Column(name = "depth_rank", nullable = false)
    private Integer depthRank;

    /** Null only at the top of the continuum (general anaesthesia has nothing above it). */
    @Column(name = "rescue_capability_level_code", length = 32)
    private String rescueCapabilityLevelCode;

    @Column(name = "monitoring_required", nullable = false)
    private String monitoringRequired;

    @Column(name = "provider_competence_required", nullable = false)
    private String providerCompetenceRequired;

    @Column(name = "typical_recovery_criteria")
    private String typicalRecoveryCriteria;

    @Column(name = "source_citation")
    private String sourceCitation;

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getLevelCode() { return levelCode; }
    public String getLevelLabel() { return levelLabel; }
    public Integer getDepthRank() { return depthRank; }
    public String getRescueCapabilityLevelCode() { return rescueCapabilityLevelCode; }
    public String getMonitoringRequired() { return monitoringRequired; }
    public String getProviderCompetenceRequired() { return providerCompetenceRequired; }
    public String getTypicalRecoveryCriteria() { return typicalRecoveryCriteria; }
    public String getSourceCitation() { return sourceCitation; }
}
