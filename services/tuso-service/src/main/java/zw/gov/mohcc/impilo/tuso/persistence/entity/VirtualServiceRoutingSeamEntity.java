package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Routing seam: how a virtual service is reachable through existing routing
 * substrate. {@code targetRef} is the frozen pool key — PCT referrals carry
 * {@code routing_kind='POOL'} + {@code routing_pool_id=<target_ref>}.
 */
@Entity
@Table(name = "virtual_service_routing_seam", schema = "tuso")
public class VirtualServiceRoutingSeamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "virtual_service_id", nullable = false)
    private VirtualServiceEntity virtualService;

    @Column(name = "routing_type", nullable = false, length = 64)
    private String routingType;

    @Column(name = "target_ref", nullable = false, length = 128)
    private String targetRef;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "active", nullable = false)
    private boolean active;

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
    public String getRoutingType() { return routingType; }
    public void setRoutingType(String routingType) { this.routingType = routingType; }
    public String getTargetRef() { return targetRef; }
    public void setTargetRef(String targetRef) { this.targetRef = targetRef; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
