package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Ordered service line of a virtual service-delivery entity. */
@Entity
@Table(name = "virtual_service_line", schema = "tuso")
public class VirtualServiceLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "virtual_service_id", nullable = false)
    private VirtualServiceEntity virtualService;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public VirtualServiceEntity getVirtualService() { return virtualService; }
    public void setVirtualService(VirtualServiceEntity virtualService) { this.virtualService = virtualService; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
