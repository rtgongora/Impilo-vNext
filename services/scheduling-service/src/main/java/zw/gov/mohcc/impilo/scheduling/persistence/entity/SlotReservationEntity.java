package zw.gov.mohcc.impilo.scheduling.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "slot_reservations", schema = "scheduling")
public class SlotReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "hold_token", nullable = false)
    private String holdToken;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected SlotReservationEntity() {}

    public SlotReservationEntity(UUID tenantId, String resourceId, LocalDate slotDate, LocalTime startTime, String holdToken) {
        this.tenantId = tenantId;
        this.resourceId = resourceId;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.holdToken = holdToken;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getResourceId() { return resourceId; }
    public LocalDate getSlotDate() { return slotDate; }
    public LocalTime getStartTime() { return startTime; }
    public String getHoldToken() { return holdToken; }
}
