package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_cost_methods")
public class CostMethodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, length = 20)
    private CostMethodType methodType;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "config", nullable = false, columnDefinition = "jsonb")
    private String config = "{}";

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CostMethodType getMethodType() { return methodType; }
    public void setMethodType(CostMethodType methodType) { this.methodType = methodType; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
