package zw.gov.mohcc.impilo.booking.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import zw.gov.mohcc.impilo.booking.domain.ConsentStatus;
import zw.gov.mohcc.impilo.booking.domain.MvumoType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_mvumo", schema = "booking")
public class BookingMvumoEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "mvumo_record_id", nullable = false)
    private String mvumoRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mvumo_type", nullable = false, length = 32)
    private MvumoType mvumoType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ConsentStatus status = ConsentStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public String getMvumoRecordId() { return mvumoRecordId; }
    public void setMvumoRecordId(String mvumoRecordId) { this.mvumoRecordId = mvumoRecordId; }
    public MvumoType getMvumoType() { return mvumoType; }
    public void setMvumoType(MvumoType mvumoType) { this.mvumoType = mvumoType; }
    public ConsentStatus getStatus() { return status; }
    public void setStatus(ConsentStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
