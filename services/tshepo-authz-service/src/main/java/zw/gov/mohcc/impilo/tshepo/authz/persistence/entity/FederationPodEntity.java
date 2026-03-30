package zw.gov.mohcc.impilo.tshepo.authz.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "federation_pod", schema = "tshepo_authz")
public class FederationPodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pod_id", nullable = false, unique = true, length = 128)
    private String podId;

    @Column(name = "pod_name", nullable = false)
    private String podName;

    @Column(name = "pod_type", nullable = false, length = 32)
    private String podType;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "authority", columnDefinition = "jsonb")
    private String authority;

    @Column(name = "obligations", columnDefinition = "jsonb")
    private String obligations;

    @Column(name = "routing_config", columnDefinition = "jsonb")
    private String routingConfig;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.registeredAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ── Getters and Setters ────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPodId() { return podId; }
    public void setPodId(String podId) { this.podId = podId; }

    public String getPodName() { return podName; }
    public void setPodName(String podName) { this.podName = podName; }

    public String getPodType() { return podType; }
    public void setPodType(String podType) { this.podType = podType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAuthority() { return authority; }
    public void setAuthority(String authority) { this.authority = authority; }

    public String getObligations() { return obligations; }
    public void setObligations(String obligations) { this.obligations = obligations; }

    public String getRoutingConfig() { return routingConfig; }
    public void setRoutingConfig(String routingConfig) { this.routingConfig = routingConfig; }

    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
