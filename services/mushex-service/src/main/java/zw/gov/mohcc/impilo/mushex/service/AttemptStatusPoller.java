package zw.gov.mohcc.impilo.mushex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentAttemptEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.AttemptStatus;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentAttemptRepository;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterRegistry;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterResponse;
import zw.gov.mohcc.impilo.mushex.service.adapter.PaymentRailAdapter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * The asynchronous settlement completion loop for EXTERNAL rails: polls open
 * attempts on live-capable adapters via {@code checkStatus} and settles the
 * intent when the provider reports payment. Provider result webhooks are the
 * fast path; this poller is the backstop that guarantees a lost webhook can
 * never strand a paid transaction unsettled.
 *
 * <p>Only rails whose adapter is {@code liveCapable()} are polled — stubs and
 * the sandbox never generate poll traffic. Every transition here is idempotent
 * (attempt status guards + intent recordPayment convergence).</p>
 */
@Service
public class AttemptStatusPoller {

    private static final Logger log = LoggerFactory.getLogger(AttemptStatusPoller.class);

    private final PaymentAttemptRepository attemptRepository;
    private final AdapterRegistry adapterRegistry;
    private final PaymentIntentService paymentIntentService;

    public AttemptStatusPoller(PaymentAttemptRepository attemptRepository,
                               AdapterRegistry adapterRegistry,
                               PaymentIntentService paymentIntentService) {
        this.attemptRepository = attemptRepository;
        this.adapterRegistry = adapterRegistry;
        this.paymentIntentService = paymentIntentService;
    }

    @Scheduled(fixedDelayString = "${mushex.attempt-poll-interval-ms:15000}")
    @Transactional
    public void pollOpenAttempts() {
        List<PaymentAttemptEntity> open =
                attemptRepository.findTop50ByStatusInAndAdapterRefIsNotNullOrderByRequestedAtAsc(
                        List.of(AttemptStatus.INITIATED, AttemptStatus.PROCESSING));
        for (PaymentAttemptEntity attempt : open) {
            pollOne(attempt);
        }
    }

    /** Package-visible for tests. */
    void pollOne(PaymentAttemptEntity attempt) {
        AdapterType rail = attempt.getAdapterType();
        PaymentRailAdapter adapter;
        try {
            adapter = adapterRegistry.getAdapter(rail);
        } catch (IllegalArgumentException noAdapter) {
            return;
        }
        if (!adapter.liveCapable()) {
            return; // stubs/sandbox never poll a provider
        }
        try {
            AdapterResponse status = adapter.checkStatus(attempt.getAdapterRef(), Map.of());
            switch (status.status() == null ? "PENDING" : status.status().toUpperCase()) {
                case "SUCCESS", "SUCCEEDED", "COMPLETED" -> {
                    attempt.setStatus(AttemptStatus.SUCCEEDED);
                    attempt.setCompletedAt(OffsetDateTime.now());
                    attemptRepository.save(attempt);
                    paymentIntentService.recordPayment(attempt.getIntentId(), attempt.getAmount());
                    log.info("Poller settled attempt {} → intent {} PAID", attempt.getId(), attempt.getIntentId());
                }
                case "CANCELLED", "CANCELED" -> {
                    attempt.setStatus(AttemptStatus.CANCELLED);
                    attempt.setCompletedAt(OffsetDateTime.now());
                    attemptRepository.save(attempt);
                    log.info("Poller: attempt {} CANCELLED at provider", attempt.getId());
                }
                case "FAILED", "ERROR", "DECLINED" -> {
                    attempt.setStatus(AttemptStatus.FAILED);
                    attempt.setCompletedAt(OffsetDateTime.now());
                    attemptRepository.save(attempt);
                    log.warn("Poller: attempt {} FAILED at provider", attempt.getId());
                }
                default -> {
                    // still pending — keep polling
                }
            }
        } catch (Exception e) {
            // Transient provider/network trouble: log and let the next tick retry.
            log.warn("Status poll failed for attempt {} (rail {}): {}", attempt.getId(), rail, e.getMessage());
        }
    }
}
