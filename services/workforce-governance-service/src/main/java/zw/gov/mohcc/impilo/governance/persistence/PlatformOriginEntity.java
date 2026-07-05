package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The singleton Platform Origin root record — the origin of trust for the
 * platform itself, above every country operation. NOT tenant-scoped.
 *
 * <p>The {@code singleton} guard column (always {@code true} and UNIQUE)
 * enforces at most one row. Root account references and the recovery policy
 * are stored as JSON documents; mutations occur only through APPROVED
 * {@code PLATFORM_ACTION} access requests under the two-person rule.</p>
 */
@Entity
@Table(name = "wgv_platform_origin")
public class PlatformOriginEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "singleton", nullable = false)
    private boolean singleton = true;

    @Column(name = "root_account_refs", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<Map<String, Object>> rootAccountRefs;

    @Column(name = "recovery_policy", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> recoveryPolicy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        singleton = true;
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public boolean isSingleton() { return singleton; }
    public List<Map<String, Object>> getRootAccountRefs() { return rootAccountRefs; }
    public void setRootAccountRefs(List<Map<String, Object>> rootAccountRefs) { this.rootAccountRefs = rootAccountRefs; }
    public Map<String, Object> getRecoveryPolicy() { return recoveryPolicy; }
    public void setRecoveryPolicy(Map<String, Object> recoveryPolicy) { this.recoveryPolicy = recoveryPolicy; }
    public Instant getCreatedAt() { return createdAt; }
}
