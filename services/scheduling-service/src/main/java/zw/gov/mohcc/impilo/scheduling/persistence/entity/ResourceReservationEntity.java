package zw.gov.mohcc.impilo.scheduling.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A held reservation of a resource for a time window. The conflict-detection
 * substrate: overlapping HELD reservations for the same kind+ref+window are the
 * real double-book signal. Mirrors the slot_reservations unique-constraint style.
 */
@Entity
@Table(name = "resource_reservation", schema = "scheduling")
public class ResourceReservationEntity {

    public static final String KIND_ROOM = "ROOM";
    public static final String KIND_PRACTITIONER = "PRACTITIONER";
    public static final String KIND_EQUIPMENT = "EQUIPMENT";
    public static final String KIND_INSTRUMENT_SET = "INSTRUMENT_SET";
    public static final String KIND_RECOVERY_BED = "RECOVERY_BED";
    public static final String KIND_ICU_BED = "ICU_BED";
    public static final String KIND_PATIENT = "PATIENT";

    public static final String STATUS_HELD = "HELD";
    public static final String STATUS_RELEASED = "RELEASED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "or_list_item_id")
    private UUID orListItemId;

    @Column(name = "resource_kind", nullable = false)
    private String resourceKind;

    @Column(name = "resource_ref", nullable = false)
    private String resourceRef;

    @Column(name = "reservation_date", nullable = false)
    private LocalDate reservationDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "status", nullable = false)
    private String status = STATUS_HELD;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ResourceReservationEntity() {
    }

    public ResourceReservationEntity(UUID id, UUID tenantId, UUID sessionId, UUID orListItemId,
                                     String resourceKind, String resourceRef,
                                     LocalDate reservationDate, LocalTime startTime, LocalTime endTime) {
        this.id = id;
        this.tenantId = tenantId;
        this.sessionId = sessionId;
        this.orListItemId = orListItemId;
        this.resourceKind = resourceKind;
        this.resourceRef = resourceRef;
        this.reservationDate = reservationDate;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getResourceKind() {
        return resourceKind;
    }

    public String getResourceRef() {
        return resourceRef;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getStatus() {
        return status;
    }

    public void release() {
        this.status = STATUS_RELEASED;
    }
}
