package zw.gov.mohcc.impilo.tshepo.authz.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * What authorization WOULD have decided if the PDP could see a browser session's roles.
 *
 * <p><b>This is not an authorization record.</b> {@code PolicyDecisionLogEntity} remains the only
 * record of what was actually decided. Nothing here is ever read by an enforcement path, and
 * nothing written here can change a verdict — by the time a row exists, the production decision
 * was already made at Envoy, before the BFF ran.</p>
 *
 * <p><b>There is deliberately no field for a credential.</b> Deriving a shadow verdict requires
 * the person's access token; that token is transient memory for the length of one evaluation and
 * is never written, logged or traced. Only derived facts land here. If a future change needs "the
 * token that produced this", the answer is no.</p>
 */
@Entity
@Table(name = "shadow_decision_log", schema = "tshepo_authz")
public class ShadowDecisionLogEntity {

    /** No delta: production and shadow agree. The result we hope to see most often. */
    public static final String DELTA_NONE = "NONE";
    /** Live traffic that works today would START being refused at enforcement. The dangerous one. */
    public static final String DELTA_PERMIT_TO_DENY = "PERMIT_TO_DENY";
    /** Traffic refused today would start being allowed — the 489 dormant rules waking up. */
    public static final String DELTA_DENY_TO_PERMIT = "DENY_TO_PERMIT";
    /** Same verdict, different principal classification. */
    public static final String DELTA_ACTOR_TYPE_CHANGE = "ACTOR_TYPE_CHANGE";
    /** Same verdict, different rule granted it — worth knowing before enforcement. */
    public static final String DELTA_RULE_CHANGE = "RULE_CHANGE";

    public static final String OUTCOME_OK = "OK";
    public static final String OUTCOME_PDP_ERROR = "PDP_ERROR";
    public static final String OUTCOME_TIMEOUT = "TIMEOUT";
    public static final String OUTCOME_BEARER_UNRESOLVED = "BEARER_UNRESOLVED";
    public static final String OUTCOME_RECORDER_ERROR = "RECORDER_ERROR";

    public static final String MODE_ASYNC = "ASYNC";
    public static final String MODE_INLINE_CANARY = "INLINE_CANARY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "actor_id", length = 128)
    private String actorId;

    @Column(name = "method", length = 10)
    private String method;

    @Column(name = "path")
    private String path;

    @Column(name = "resource_type", length = 128)
    private String resourceType;

    @Column(name = "action", length = 64)
    private String action;

    @Column(name = "prod_verdict", length = 16)
    private String prodVerdict;

    @Column(name = "prod_actor_type", length = 32)
    private String prodActorType;

    @Column(name = "shadow_verdict", length = 16)
    private String shadowVerdict;

    @Column(name = "shadow_deny_reason", length = 64)
    private String shadowDenyReason;

    @Column(name = "shadow_matched_rule", length = 128)
    private String shadowMatchedRule;

    /** Comma-joined role names. Derived authority, never a credential. */
    @Column(name = "shadow_roles")
    private String shadowRoles;

    @Column(name = "principal_class", length = 16)
    private String principalClass;

    @Column(name = "active_persona", length = 48)
    private String activePersona;

    @Column(name = "legacy_actor_type", length = 32)
    private String legacyActorType;

    @Column(name = "delta_kind", nullable = false, length = 32)
    private String deltaKind = DELTA_NONE;

    @Column(name = "obligations_delta")
    private String obligationsDelta;

    @Column(name = "probe_mode", length = 16)
    private String probeMode;

    @Column(name = "probe_outcome", nullable = false, length = 24)
    private String probeOutcome = OUTCOME_OK;

    @Column(name = "pdp_millis")
    private Integer pdpMillis;

    @Column(name = "total_added_millis")
    private Integer totalAddedMillis;

    @Column(name = "route_class", length = 64)
    private String routeClass;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID v) { this.correlationId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getActorId() { return actorId; }
    public void setActorId(String v) { this.actorId = v; }
    public String getMethod() { return method; }
    public void setMethod(String v) { this.method = v; }
    public String getPath() { return path; }
    public void setPath(String v) { this.path = v; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String v) { this.resourceType = v; }
    public String getAction() { return action; }
    public void setAction(String v) { this.action = v; }
    public String getProdVerdict() { return prodVerdict; }
    public void setProdVerdict(String v) { this.prodVerdict = v; }
    public String getProdActorType() { return prodActorType; }
    public void setProdActorType(String v) { this.prodActorType = v; }
    public String getShadowVerdict() { return shadowVerdict; }
    public void setShadowVerdict(String v) { this.shadowVerdict = v; }
    public String getShadowDenyReason() { return shadowDenyReason; }
    public void setShadowDenyReason(String v) { this.shadowDenyReason = v; }
    public String getShadowMatchedRule() { return shadowMatchedRule; }
    public void setShadowMatchedRule(String v) { this.shadowMatchedRule = v; }
    public String getShadowRoles() { return shadowRoles; }
    public void setShadowRoles(String v) { this.shadowRoles = v; }
    public String getPrincipalClass() { return principalClass; }
    public void setPrincipalClass(String v) { this.principalClass = v; }
    public String getActivePersona() { return activePersona; }
    public void setActivePersona(String v) { this.activePersona = v; }
    public String getLegacyActorType() { return legacyActorType; }
    public void setLegacyActorType(String v) { this.legacyActorType = v; }
    public String getDeltaKind() { return deltaKind; }
    public void setDeltaKind(String v) { this.deltaKind = v; }
    public String getObligationsDelta() { return obligationsDelta; }
    public void setObligationsDelta(String v) { this.obligationsDelta = v; }
    public String getProbeMode() { return probeMode; }
    public void setProbeMode(String v) { this.probeMode = v; }
    public String getProbeOutcome() { return probeOutcome; }
    public void setProbeOutcome(String v) { this.probeOutcome = v; }
    public Integer getPdpMillis() { return pdpMillis; }
    public void setPdpMillis(Integer v) { this.pdpMillis = v; }
    public Integer getTotalAddedMillis() { return totalAddedMillis; }
    public void setTotalAddedMillis(Integer v) { this.totalAddedMillis = v; }
    public String getRouteClass() { return routeClass; }
    public void setRouteClass(String v) { this.routeClass = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
