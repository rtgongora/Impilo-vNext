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
@Table(name = "cv_network_members")
public class NetworkMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "network_id", nullable = false)
    private UUID networkId;

    @Column(name = "provider_id", nullable = false, length = 128)
    private String providerId;

    @Column(name = "contract_id")
    private UUID contractId;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "removed_at")
    private OffsetDateTime removedAt;

    public NetworkMemberEntity() {}

    @PrePersist
    void prePersist() {
        if (joinedAt == null) {
            joinedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getNetworkId() { return networkId; }
    public void setNetworkId(UUID networkId) { this.networkId = networkId; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public UUID getContractId() { return contractId; }
    public void setContractId(UUID contractId) { this.contractId = contractId; }
    public OffsetDateTime getJoinedAt() { return joinedAt; }
    public OffsetDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(OffsetDateTime removedAt) { this.removedAt = removedAt; }
}
