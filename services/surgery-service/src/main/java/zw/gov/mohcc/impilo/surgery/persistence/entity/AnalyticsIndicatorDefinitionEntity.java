package zw.gov.mohcc.impilo.surgery.persistence.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * A surgical-pack analytics indicator (§23) — governed content declaring what the indicator IS
 * and its real computation status today, not an execution engine recomputing what
 * reporting-service already computes. See V008's own header for the full boundary rationale and
 * the three-way count reconciliation across audit.md/dak-baseline.md/the real projection.
 */
@Entity
@Table(name = "analytics_indicator_definition", schema = "surgery")
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

    @Column(name = "lancet_core_indicator")
    private String lancetCoreIndicator;

    @Column(name = "content_maturity", nullable = false, length = 32)
    private String contentMaturity = "ENGINEERING_SEED";

    @Column(name = "approving_authority", nullable = false, length = 64)
    private String approvingAuthority = "PENDING_MOHCC_RATIFICATION";

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
    public String getLancetCoreIndicator() { return lancetCoreIndicator; }
    public String getContentMaturity() { return contentMaturity; }
    public String getApprovingAuthority() { return approvingAuthority; }
    public int getDisplayOrder() { return displayOrder; }
}
