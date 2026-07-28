package zw.gov.mohcc.impilo.procedures.persistence.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * A pipeline analytics indicator (§26) — governed content declaring what the indicator IS and
 * its real computation status today, not an execution engine recomputing what reporting-service
 * already computes. See V010's own header for the full boundary rationale.
 */
@Entity
@Table(name = "analytics_indicator_definition", schema = "procedures")
public class AnalyticsIndicatorDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "indicator_code", nullable = false, length = 64)
    private String indicatorCode;

    @Column(name = "indicator_name", nullable = false)
    private String indicatorName;

    @Column(name = "numerator_description", nullable = false)
    private String numeratorDescription;

    @Column(name = "denominator_description")
    private String denominatorDescription;

    @Column(name = "computation_status", nullable = false, length = 24)
    private String computationStatus;

    @Column(name = "executable_via")
    private String executableVia;

    @Column(name = "gap_reason")
    private String gapReason;

    @Column(name = "owning_service", length = 64)
    private String owningService;

    @Column(name = "delegated_out_of_scope", nullable = false)
    private boolean delegatedOutOfScope;

    @Column(name = "source_citation")
    private String sourceCitation;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getIndicatorCode() { return indicatorCode; }
    public String getIndicatorName() { return indicatorName; }
    public String getNumeratorDescription() { return numeratorDescription; }
    public String getDenominatorDescription() { return denominatorDescription; }
    public String getComputationStatus() { return computationStatus; }
    public String getExecutableVia() { return executableVia; }
    public String getGapReason() { return gapReason; }
    public String getOwningService() { return owningService; }
    public boolean isDelegatedOutOfScope() { return delegatedOutOfScope; }
    public String getSourceCitation() { return sourceCitation; }
    public int getDisplayOrder() { return displayOrder; }
}
