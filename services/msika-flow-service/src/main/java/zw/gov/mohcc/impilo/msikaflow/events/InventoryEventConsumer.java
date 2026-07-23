package zw.gov.mohcc.impilo.msikaflow.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.core.OfferStateMachine;
import zw.gov.mohcc.impilo.msikaflow.domain.OfferStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.ReservationStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferLineEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.ReservationEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.FulfillmentOfferLineRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.FulfillmentOfferRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.ReservationRepository;

import java.util.Locale;
import java.util.Optional;

/**
 * OF-B11 — the real DURA reservation projection consumer (replaces the
 * historical no-op logger). DURA's {@code inv_stock_reservations} is the sole
 * reservation truth; {@code mf_reservations} rows are a demoted read-projection
 * updated here by {@code reservationRef} from
 * {@code inventory.reservation.status_changed} events (payload
 * {@code {reservationRef, status}}).
 *
 * <p>Fail-close TTL binding (§9.4): a reservation that expires or is released
 * while its offer is still SELECTED/REVALIDATING expires the offer — no
 * component may honour an expired counterpart.</p>
 */
@Service
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final ReservationRepository reservationRepository;
    private final FulfillmentOfferLineRepository offerLineRepository;
    private final FulfillmentOfferRepository offerRepository;
    private final ObjectMapper objectMapper;

    public InventoryEventConsumer(ReservationRepository reservationRepository,
                                  FulfillmentOfferLineRepository offerLineRepository,
                                  FulfillmentOfferRepository offerRepository,
                                  ObjectMapper objectMapper) {
        this.reservationRepository = reservationRepository;
        this.offerLineRepository = offerLineRepository;
        this.offerRepository = offerRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {
                    "inventory.reservation.status_changed",
                    "impilo.inventory.reservation"
            },
            groupId = "msika-flow-service"
    )
    @Transactional
    public void onReservationStatusChanged(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String reservationRef = node.path("reservationRef").asText(null);
            String status = node.path("status").asText(null);
            if (reservationRef == null || reservationRef.isBlank() || status == null || status.isBlank()) {
                log.warn("Inventory reservation event missing reservationRef/status: {}", message);
                return;
            }
            applyStatus(reservationRef, status);
        } catch (Exception e) {
            log.error("Failed to process inventory event: {}", e.getMessage(), e);
        }
    }

    void applyStatus(String reservationRef, String duraStatus) {
        ReservationStatus mapped = mapStatus(duraStatus);
        if (mapped == null) {
            log.warn("Unknown DURA reservation status '{}' for ref {}", duraStatus, reservationRef);
            return;
        }
        Optional<ReservationEntity> row = reservationRepository.findFirstByReservationRef(reservationRef);
        if (row.isPresent()) {
            ReservationEntity projection = row.get();
            projection.setStatus(mapped);
            reservationRepository.save(projection);
            log.info("Reservation projection updated: ref={} status={}", reservationRef, mapped);
        } else {
            // Projection row may not exist yet (event raced the commitment write) — not an error.
            log.info("No mf_reservations projection row for DURA ref {} (status {})", reservationRef, mapped);
        }

        if (mapped == ReservationStatus.EXPIRED || mapped == ReservationStatus.RELEASED) {
            failCloseLinkedOffer(reservationRef, mapped);
        }
    }

    /** §9.4 binding: the DURA hold and the offer expire together, fail-close. */
    private void failCloseLinkedOffer(String reservationRef, ReservationStatus mapped) {
        for (FulfillmentOfferLineEntity line : offerLineRepository.findByDuraReservationRef(reservationRef)) {
            offerRepository.findById(line.getOfferId()).ifPresent(offer -> {
                if (offer.getStatus() == OfferStatus.SELECTED || offer.getStatus() == OfferStatus.REVALIDATING) {
                    OfferStatus target = mapped == ReservationStatus.EXPIRED
                            ? OfferStatus.EXPIRED : OfferStatus.FAILED_REVALIDATION;
                    if (OfferStateMachine.canTransition(offer.getStatus(), target)) {
                        offer.setStatus(target);
                        offer.setStatusReason("RESERVATION_" + mapped.name());
                        offerRepository.save(offer);
                        log.warn("Offer {} fail-closed ({}): DURA reservation {} became {}",
                                offer.getOfferId(), target, reservationRef, mapped);
                    }
                }
            });
        }
    }

    private static ReservationStatus mapStatus(String duraStatus) {
        return switch (duraStatus.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> ReservationStatus.CONFIRMED;
            case "RELEASED" -> ReservationStatus.RELEASED;
            case "CONSUMED" -> ReservationStatus.CONSUMED;
            case "EXPIRED" -> ReservationStatus.EXPIRED;
            default -> null;
        };
    }

    @KafkaListener(
            topics = {
                    "pharmacy.fulfillment.status_changed",
                    "impilo.pharmacy.fulfillment"
            },
            groupId = "msika-flow-service"
    )
    public void onPharmacyFulfillmentChanged(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String orderId = node.path("orderId").asText();
            String status = node.path("status").asText();

            log.info("Pharmacy fulfillment status: orderId={} status={}", orderId, status);

        } catch (Exception e) {
            log.error("Failed to process pharmacy event: {}", e.getMessage(), e);
        }
    }
}
