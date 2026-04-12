package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cv_provider_networks")
public class ProviderNetworkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "network_id", nullable = false, unique = true, updatable = false)
    private UUID networkId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "payer_id", nullable = false, length = 128)
    private String payerId;

    @Column(name = "network_type", nullable = false, length = 32)
    private String networkType = "PREFERRED";

    @Column(name = "service_area", columnDefinition = "jsonb")
    private String serviceArea = "[]";

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public ProviderNetworkEntity() {}

    @PrePersist
    void prePersist() {
        if (networkId == null) {
            networkId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public UUID getNetworkId() { return networkId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPayerId() { return payerId; }
    public void setPayerId(String payerId) { this.payerId = payerId; }
    public String getNetworkType() { return networkType; }
    public void setNetworkType(String networkType) { this.networkType = networkType; }
    public String getServiceArea() { return serviceArea; }
    public void setServiceArea(String serviceArea) { this.serviceArea = serviceArea; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
