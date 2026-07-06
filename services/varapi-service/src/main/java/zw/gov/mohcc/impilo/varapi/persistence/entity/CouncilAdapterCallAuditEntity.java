package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Audit trail of every LIVE council adapter call (IATG Wave 2, Channel A). Mirrors the
 * intent of the connector-fhir-adapter {@code cfa_relay_audit} row: who/what was called,
 * the endpoint, the decision and the honest outcome. One row per adapter invocation that
 * actually reached the seam (i.e. a live registration existed).
 */
@Entity
@Table(name = "council_adapter_call_audit", schema = "varapi")
public class CouncilAdapterCallAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "council_code", nullable = false, length = 64)
    private String councilCode;

    @Column(name = "endpoint_url", length = 512)
    private String endpointUrl;

    @Column(name = "temporary_provider_public_id", length = 64)
    private String temporaryProviderPublicId;

    /** Adapter Result name (IDENTITY_MATCH | REGISTRY_MATCH | NO_MATCH | FORMAT_INVALID | UNREACHABLE). */
    @Column(name = "result", nullable = false, length = 32)
    private String result;

    /** Whether the authority was reachable — false ⇒ honest degraded, never a PASS. */
    @Column(name = "reachable", nullable = false)
    private boolean reachable;

    /** The authority's graded confidence (never platform-fabricated); null when no answer. */
    @Column(name = "confidence", precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(name = "matched_ref", length = 128)
    private String matchedRef;

    /** Human-readable disposition, e.g. DENIED_UNREACHABLE / DEGRADED_UNREACHABLE. */
    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public CouncilAdapterCallAuditEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getCouncilCode() { return councilCode; }
    public void setCouncilCode(String councilCode) { this.councilCode = councilCode; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getTemporaryProviderPublicId() { return temporaryProviderPublicId; }
    public void setTemporaryProviderPublicId(String temporaryProviderPublicId) {
        this.temporaryProviderPublicId = temporaryProviderPublicId;
    }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public boolean isReachable() { return reachable; }
    public void setReachable(boolean reachable) { this.reachable = reachable; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public String getMatchedRef() { return matchedRef; }
    public void setMatchedRef(String matchedRef) { this.matchedRef = matchedRef; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
