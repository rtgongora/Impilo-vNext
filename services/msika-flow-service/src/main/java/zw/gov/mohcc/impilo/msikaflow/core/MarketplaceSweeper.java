package zw.gov.mohcc.impilo.msikaflow.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * §9.3/§9.4 timers (OF-B4/OF-B6): offer TTL expiry, offer-window close
 * (→ OFFERS_AVAILABLE or FAILED_NO_OFFERS), selection-window lapse (→ EXPIRED).
 * Delegates to {@link MarketplaceRequestService} so the sweep logic stays unit-testable.
 */
@Component
public class MarketplaceSweeper {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceSweeper.class);

    private final MarketplaceRequestService requestService;
    private final CommitmentService commitmentService;

    public MarketplaceSweeper(MarketplaceRequestService requestService,
                              CommitmentService commitmentService) {
        this.requestService = requestService;
        this.commitmentService = commitmentService;
    }

    @Scheduled(fixedDelayString = "${msika-flow.marketplace.sweep-interval-ms:60000}")
    public void sweep() {
        OffsetDateTime now = OffsetDateTime.now();
        try {
            int offers = requestService.sweepExpiredOffers(now);
            int windows = requestService.sweepOfferWindows(now);
            int selections = requestService.sweepSelectionWindows(now);
            // OF-B10 §8.8.4 — AWAITING_PAYMENT retry window = reservation TTL;
            // lapse compensates (holds + claim) and fails PAYMENT_TIMEOUT.
            int payments = commitmentService.sweepAwaitingPayment(now);
            if (offers + windows + selections + payments > 0) {
                log.info("Marketplace sweep: expiredOffers={} closedOfferWindows={} "
                                + "lapsedSelectionWindows={} paymentTimeouts={}",
                        offers, windows, selections, payments);
            }
        } catch (Exception e) {
            log.error("Marketplace sweep failed: {}", e.getMessage(), e);
        }
    }
}
