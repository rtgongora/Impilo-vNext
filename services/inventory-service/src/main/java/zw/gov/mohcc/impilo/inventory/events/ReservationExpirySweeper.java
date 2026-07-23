package zw.gov.mohcc.impilo.inventory.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.inventory.core.ReservationService;

import java.time.OffsetDateTime;

/**
 * OF-B11 reservation TTL sweep (§8.9.3 fail-close): lapsed ACTIVE holds flip to
 * EXPIRED and the {@code inventory.reservation.expired.v1} + legacy
 * status-changed events ride the outbox so the msika-flow projection (and any
 * offer still honouring the hold) fail-closes with it. No zombie holds.
 */
@Component
public class ReservationExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirySweeper.class);

    private final ReservationService reservationService;

    public ReservationExpirySweeper(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(fixedDelayString = "${inventory.reservations.expiry-sweep-interval-ms:60000}")
    public void sweep() {
        try {
            int expired = reservationService.expireLapsed(OffsetDateTime.now());
            if (expired > 0) {
                log.info("Reservation expiry sweep: {} lapsed holds expired", expired);
            }
        } catch (Exception e) {
            log.error("Reservation expiry sweep failed: {}", e.getMessage(), e);
        }
    }
}
