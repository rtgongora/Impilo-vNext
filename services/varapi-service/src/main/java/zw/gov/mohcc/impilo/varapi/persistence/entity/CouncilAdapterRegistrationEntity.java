package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registration of a LIVE council / HPA verification endpoint for a single council code
 * (IATG Wave 2, Channel A). Mirrors the connector-fhir-adapter {@code cfa_relay_destinations}
 * pattern (endpointUrl, authType default NONE, authConfigJson jsonb, enabled boolean) plus
 * per-registration timeouts.
 *
 * <p>Honestly gated OFF: rows default to {@code enabled = false}. Even when the global
 * {@code varapi.council-regulatory.live-adapter-enabled} flag is on, only an
 * {@code enabled = true} registration for the tenant + council causes a live call.</p>
 */
@Entity
@Table(name = "council_adapter_registration", schema = "varapi")
public class CouncilAdapterRegistrationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Council code this endpoint verifies against (e.g. MDPCZ, NMCZ, AHPCZ). */
    @Column(name = "council_code", nullable = false, length = 64)
    private String councilCode;

    @Column(name = "endpoint_url", nullable = false, length = 512)
    private String endpointUrl;

    @Column(name = "auth_type", nullable = false, length = 32)
    private String authType = "NONE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auth_config", columnDefinition = "jsonb")
    private String authConfigJson;

    /** OFF by default — a registration must be explicitly enabled to be honoured. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "connect_timeout_ms", nullable = false)
    private int connectTimeoutMs = 3000;

    @Column(name = "read_timeout_ms", nullable = false)
    private int readTimeoutMs = 5000;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public CouncilAdapterRegistrationEntity() {}

    public CouncilAdapterRegistrationEntity(UUID tenantId, String councilCode, String endpointUrl) {
        this.tenantId = tenantId;
        this.councilCode = councilCode;
        this.endpointUrl = endpointUrl;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getCouncilCode() { return councilCode; }
    public void setCouncilCode(String councilCode) { this.councilCode = councilCode; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }

    public String getAuthConfigJson() { return authConfigJson; }
    public void setAuthConfigJson(String authConfigJson) { this.authConfigJson = authConfigJson; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
