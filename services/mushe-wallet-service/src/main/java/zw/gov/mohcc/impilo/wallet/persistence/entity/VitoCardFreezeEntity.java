package zw.gov.mohcc.impilo.wallet.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * B4: a freeze tombstone for a revoked/suspended VITO card that had no linked money
 * card at revoke time. {@code linkVitoCard} consults it so a money card linked AFTER
 * the revoke is frozen immediately, closing the link-after-event fail-open.
 */
@Entity
@Table(name = "vito_card_freeze", schema = "mushe")
public class VitoCardFreezeEntity {

    @Id
    @Column(name = "vito_card_number", nullable = false, updatable = false, length = 64)
    private String vitoCardNumber;

    @Column(name = "event_type", nullable = false, length = 48)
    private String eventType;

    @Column(name = "reason", length = 256)
    private String reason;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = OffsetDateTime.now();
        }
    }

    public String getVitoCardNumber() { return vitoCardNumber; }
    public void setVitoCardNumber(String vitoCardNumber) { this.vitoCardNumber = vitoCardNumber; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime receivedAt) { this.receivedAt = receivedAt; }
}
