package zw.gov.mohcc.impilo.mushex.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentAttemptEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AttemptStatus;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentAttemptRepository;
import zw.gov.mohcc.impilo.mushex.integration.paynow.PaynowClient;
import zw.gov.mohcc.impilo.mushex.service.PaymentIntentService;
import zw.gov.mohcc.impilo.mushex.service.adapter.PaynowGatewayAdapter;

import java.time.OffsetDateTime;

/**
 * Paynow's {@code resulturl} endpoint. Paynow POSTs a form-encoded status
 * message here when a transaction completes; integrity is the in-payload
 * SHA-512 hash (field values + integration key), not a header signature —
 * which is why this cannot ride the generic JSON webhook controller.
 *
 * <p>Fail-closed: a message whose hash does not verify is rejected and changes
 * nothing. The poll loop remains the settlement backstop when result posts are
 * lost — both paths converge on the same idempotent attempt/intent updates.</p>
 */
@RestController
@RequestMapping("/mushex/v1/adapters/paynow")
@ConditionalOnBean(PaynowGatewayAdapter.class)
public class PaynowResultController {

    private static final Logger log = LoggerFactory.getLogger(PaynowResultController.class);

    private final PaynowGatewayAdapter adapter;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentIntentService paymentIntentService;

    public PaynowResultController(PaynowGatewayAdapter adapter,
                                  PaymentAttemptRepository attemptRepository,
                                  PaymentIntentService paymentIntentService) {
        this.adapter = adapter;
        this.attemptRepository = attemptRepository;
        this.paymentIntentService = paymentIntentService;
    }

    @PostMapping(value = "/result", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> onResult(@RequestBody String rawFormBody) {
        PaynowClient.StatusResult status = adapter.client().parseStatus(rawFormBody);
        if (!status.hashValid()) {
            log.warn("Paynow result post FAILED hash verification — rejected");
            return ResponseEntity.badRequest().body("hash verification failed");
        }

        // Paynow's `reference` is OUR reference — the intent id we initiated with.
        String intentId = status.reference();
        if (intentId == null || intentId.isBlank()) {
            log.warn("Paynow result post carries no reference — ignored");
            return ResponseEntity.badRequest().body("missing reference");
        }
        PaymentAttemptEntity attempt = attemptRepository
                .findByIntentIdOrderByRequestedAtAsc(intentId).stream()
                .filter(a -> a.getStatus() == AttemptStatus.INITIATED
                        || a.getStatus() == AttemptStatus.PROCESSING)
                .reduce((first, second) -> second) // latest open attempt
                .orElse(null);
        if (attempt == null) {
            log.warn("Paynow result for intent {} has no open attempt — acknowledged without change", intentId);
            return ResponseEntity.ok("no open attempt");
        }

        if (status.paid()) {
            attempt.setStatus(AttemptStatus.SUCCEEDED);
            attempt.setCompletedAt(OffsetDateTime.now());
            attempt.setRawSummary(rawFormBody);
            attemptRepository.save(attempt);
            // recordPayment is idempotent against an already-PAID intent via the
            // controller/poller paths converging on intent status.
            paymentIntentService.recordPayment(attempt.getIntentId(), attempt.getAmount());
            log.info("Paynow result: intent {} PAID (paynowRef={})", intentId, status.paynowReference());
        } else if (status.cancelled()) {
            attempt.setStatus(AttemptStatus.CANCELLED);
            attempt.setCompletedAt(OffsetDateTime.now());
            attempt.setRawSummary(rawFormBody);
            attemptRepository.save(attempt);
            log.info("Paynow result: intent {} attempt CANCELLED", intentId);
        } else {
            log.info("Paynow result: intent {} interim status '{}' — no change", intentId, status.status());
        }
        return ResponseEntity.ok("ok");
    }
}
