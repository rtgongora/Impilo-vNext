package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Queue definition owned by TUSO and materialised into PCT with
 * {@code sourceRef = '{vsUid}:{queueKey}'}. Deliberately carries NO status —
 * runtime queue status (LIVE vs AWAITING_BACKEND) is computed from what PCT
 * actually materialised.
 */
@Entity
@Table(name = "virtual_service_queue_def", schema = "tuso")
public class VirtualServiceQueueDefEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "virtual_service_id", nullable = false)
    private VirtualServiceEntity virtualService;

    @Column(name = "queue_key", nullable = false, length = 128)
    private String queueKey;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "default_sla_minutes")
    private Integer defaultSlaMinutes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules", columnDefinition = "jsonb")
    private String rules = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public VirtualServiceEntity getVirtualService() { return virtualService; }
    public void setVirtualService(VirtualServiceEntity virtualService) { this.virtualService = virtualService; }
    public String getQueueKey() { return queueKey; }
    public void setQueueKey(String queueKey) { this.queueKey = queueKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getDefaultSlaMinutes() { return defaultSlaMinutes; }
    public void setDefaultSlaMinutes(Integer defaultSlaMinutes) { this.defaultSlaMinutes = defaultSlaMinutes; }
    public String getRules() { return rules; }
    public void setRules(String rules) { this.rules = rules; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
