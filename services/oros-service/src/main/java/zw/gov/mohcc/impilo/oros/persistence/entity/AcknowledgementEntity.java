package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.oros.domain.AckType;

/**
 * Records an acknowledgement event for a clinical order.
 * Tracks department receipt, clinician review, and critical value acknowledgements.
 */
@Entity
@Table(name = "oros_acknowledgements")
public class AcknowledgementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ack_id", nullable = false)
    private UUID ackId;

    @Column(name = "order_id", nullable = false, length = 26)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ack_type", nullable = false, length = 20)
    private AckType ackType;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "acked_at", nullable = false)
    private OffsetDateTime ackedAt;

    @PrePersist
    protected void onCreate() {
        if (ackedAt == null) {
            ackedAt = OffsetDateTime.now();
        }
    }

    public UUID getAckId() { return ackId; }
    public void setAckId(UUID ackId) { this.ackId = ackId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public AckType getAckType() { return ackType; }
    public void setAckType(AckType ackType) { this.ackType = ackType; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public OffsetDateTime getAckedAt() { return ackedAt; }
    public void setAckedAt(OffsetDateTime ackedAt) { this.ackedAt = ackedAt; }
}
