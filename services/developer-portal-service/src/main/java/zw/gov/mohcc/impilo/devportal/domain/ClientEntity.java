package zw.gov.mohcc.impilo.devportal.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dvp_clients")
public class ClientEntity {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "client_name", nullable = false) private String clientName;
    @Column(name = "description") private String description;
    @Column(name = "contact_email") private String contactEmail;
    @Column(name = "status", nullable = false) private String status = "ACTIVE";
    @Column(name = "sandbox_enabled", nullable = false) private boolean sandboxEnabled = false;
    @Column(name = "sandbox_config", columnDefinition = "TEXT") private String sandboxConfig;
    @Column(name = "deprecation_posture", nullable = false) private String deprecationPosture = "NONE";
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getClientName() { return clientName; }
    public void setClientName(String v) { this.clientName = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String v) { this.contactEmail = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public boolean isSandboxEnabled() { return sandboxEnabled; }
    public void setSandboxEnabled(boolean v) { this.sandboxEnabled = v; }
    public String getSandboxConfig() { return sandboxConfig; }
    public void setSandboxConfig(String v) { this.sandboxConfig = v; }
    public String getDeprecationPosture() { return deprecationPosture; }
    public void setDeprecationPosture(String v) { this.deprecationPosture = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
