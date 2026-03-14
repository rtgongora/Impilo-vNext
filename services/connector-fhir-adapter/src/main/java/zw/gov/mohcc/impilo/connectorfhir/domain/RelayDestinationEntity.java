package zw.gov.mohcc.impilo.connectorfhir.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cfa_relay_destinations")
public class RelayDestinationEntity {
    @Id @Column(name = "destination_id") private UUID destinationId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 128) private String name;
    @Column(name = "endpoint_url", nullable = false, length = 512) private String endpointUrl;
    @Column(name = "resource_types", length = 512) private String resourceTypes = "*";
    @Column(name = "auth_type", length = 32) private String authType = "NONE";
    @Column(name = "auth_config", columnDefinition = "TEXT") private String authConfigJson;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected RelayDestinationEntity() {}
    public RelayDestinationEntity(UUID destinationId, UUID tenantId, String name, String endpointUrl,
                                   String resourceTypes) {
        this.destinationId = destinationId; this.tenantId = tenantId; this.name = name;
        this.endpointUrl = endpointUrl; this.resourceTypes = resourceTypes != null ? resourceTypes : "*";
    }

    public UUID getDestinationId() { return destinationId; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getEndpointUrl() { return endpointUrl; }
    public String getResourceTypes() { return resourceTypes; }
    public String getAuthType() { return authType; }
    public void setAuthType(String v) { this.authType = v; }
    public String getAuthConfigJson() { return authConfigJson; }
    public void setAuthConfigJson(String v) { this.authConfigJson = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
