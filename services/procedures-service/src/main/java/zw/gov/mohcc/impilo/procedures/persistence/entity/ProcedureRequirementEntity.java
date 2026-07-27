package zw.gov.mohcc.impilo.procedures.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One requirement of a procedure definition — a row, not a column.
 *
 * <p>Modelled as rows so the readiness engine iterates whatever the catalogue declares
 * rather than knowing twenty requirement names, which is the hardcoding P1 exists to
 * remove. A new requirement kind becomes a content release rather than a deployment.</p>
 */
@Entity
@Table(name = "procedure_requirement", schema = "procedures")
public class ProcedureRequirementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    @Column(name = "requirement_kind", nullable = false, length = 32)
    private String requirementKind;

    @Column(name = "requirement_code", nullable = false, length = 96)
    private String requirementCode;

    @Column(name = "requirement_label", nullable = false)
    private String requirementLabel;

    @Column(nullable = false, length = 16)
    private String obligation;

    @Column(name = "condition_expression")
    private String conditionExpression;

    /** §8: every unresolved item must have an owner. A blocker nobody owns is never cleared. */
    @Column(name = "owner_role", nullable = false, length = 64)
    private String ownerRole;

    /** Which peer answers it. Null means a human attests rather than a system resolving. */
    @Column(name = "resolver_service", length = 64)
    private String resolverService;

    @Column(name = "overridable_in_emergency", nullable = false)
    private boolean overridableInEmergency;

    /** BLOCK or WARN when the resolver cannot be reached. BLOCK is the default and the safe answer. */
    @Column(name = "on_resolver_unavailable", nullable = false, length = 16)
    private String onResolverUnavailable;

    private BigDecimal quantity;

    @Column(length = 32)
    private String unit;

    private String notes;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public UUID getId() { return id; }
    public UUID getDefinitionId() { return definitionId; }
    public String getRequirementKind() { return requirementKind; }
    public String getRequirementCode() { return requirementCode; }
    public String getRequirementLabel() { return requirementLabel; }
    public String getObligation() { return obligation; }
    public String getConditionExpression() { return conditionExpression; }
    public String getOwnerRole() { return ownerRole; }
    public String getResolverService() { return resolverService; }
    public boolean isOverridableInEmergency() { return overridableInEmergency; }
    public String getOnResolverUnavailable() { return onResolverUnavailable; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public String getNotes() { return notes; }
    public Integer getDisplayOrder() { return displayOrder; }
}
