package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "biometric_bindings", schema = "varapi")
public class BiometricBindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "biometric_ref", length = 255)
    private String biometricRef;

    @Column(name = "biometric_type", length = 30)
    private String biometricType = "FINGERPRINT";

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "bound_at")
    private Instant boundAt;

    @Column(name = "unbound_at")
    private Instant unboundAt;

    @Column(name = "bound_by", length = 255)
    private String boundBy;

    // Convenience methods

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isBound() {
        return boundAt != null && unboundAt == null;
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProviderEntity getProvider() { return provider; }
    public void setProvider(ProviderEntity provider) { this.provider = provider; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getBiometricRef() { return biometricRef; }
    public void setBiometricRef(String biometricRef) { this.biometricRef = biometricRef; }

    public String getBiometricType() { return biometricType; }
    public void setBiometricType(String biometricType) { this.biometricType = biometricType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getBoundAt() { return boundAt; }
    public void setBoundAt(Instant boundAt) { this.boundAt = boundAt; }

    public Instant getUnboundAt() { return unboundAt; }
    public void setUnboundAt(Instant unboundAt) { this.unboundAt = unboundAt; }

    public String getBoundBy() { return boundBy; }
    public void setBoundBy(String boundBy) { this.boundBy = boundBy; }
}
