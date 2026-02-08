package zw.gov.mohcc.impilo.mushex.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.api.dto.WebhookPayload;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentAttemptEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.AttemptStatus;
import zw.gov.mohcc.impilo.mushex.persistence.PaymentAttemptRepository;
import zw.gov.mohcc.impilo.mushex.service.PaymentIntentService;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterRegistry;
import zw.gov.mohcc.impilo.mushex.service.adapter.PaymentRailAdapter;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/mushex/v1/adapters")
public class AdapterWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AdapterWebhookController.class);

    private final AdapterRegistry adapterRegistry;
    private final PaymentIntentService paymentIntentService;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ObjectMapper objectMapper;

    public AdapterWebhookController(AdapterRegistry adapterRegistry,
                                    PaymentIntentService paymentIntentService,
                                    PaymentAttemptRepository paymentAttemptRepository,
                                    ObjectMapper objectMapper) {
        this.adapterRegistry = adapterRegistry;
        this.paymentIntentService = paymentIntentService;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{adapterType}/webhook")
    public ResponseEntity<ApiResponse<Map<String, String>>> handleWebhook(
            @PathVariable String adapterType,
            @RequestBody String rawPayload,
            @RequestHeader("X-Webhook-Signature") String signature) {

        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        AdapterType type = AdapterType.valueOf(adapterType.toUpperCase());
        PaymentRailAdapter adapter = adapterRegistry.getAdapter(type);

        boolean valid = adapter.verifyWebhook(signature, rawPayload, Map.of());
        if (!valid) {
            log.warn("Invalid webhook signature for adapter {} — correlationId={}", adapterType, correlationId);
            return ResponseEntity.status(400)
                    .body(ApiResponse.error("INVALID_SIGNATURE", "Webhook signature verification failed", 400, correlationId));
        }

        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, WebhookPayload.class);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload for adapter {} — correlationId={}", adapterType, correlationId, e);
            return ResponseEntity.status(400)
                    .body(ApiResponse.error("INVALID_PAYLOAD", "Failed to parse webhook payload", 400, correlationId));
        }

        PaymentAttemptEntity attempt = paymentAttemptRepository.findByAdapterRef(payload.adapterRef())
                .orElse(null);
        if (attempt == null) {
            log.warn("No attempt found for adapterRef={} — correlationId={}", payload.adapterRef(), correlationId);
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("ATTEMPT_NOT_FOUND", "No payment attempt found for adapter reference", 404, correlationId));
        }

        AttemptStatus newStatus = mapWebhookStatus(payload.status());
        attempt.setStatus(newStatus);
        attempt.setCompletedAt(OffsetDateTime.now());
        attempt.setRawSummary(rawPayload);
        paymentAttemptRepository.save(attempt);

        if (newStatus == AttemptStatus.SUCCEEDED) {
            paymentIntentService.recordPayment(attempt.getIntentId(), attempt.getAmount());
        }

        log.info("Webhook processed: adapterRef={}, status={}, intentId={} — correlationId={}",
                payload.adapterRef(), newStatus, attempt.getIntentId(), correlationId);

        return ResponseEntity.ok(ApiResponse.ok(Map.of("acknowledged", "true"), correlationId));
    }

    private AttemptStatus mapWebhookStatus(String status) {
        if (status == null) {
            return AttemptStatus.FAILED;
        }
        return switch (status.toUpperCase()) {
            case "SUCCESS", "SUCCEEDED", "COMPLETED" -> AttemptStatus.SUCCEEDED;
            case "FAILED", "ERROR", "DECLINED" -> AttemptStatus.FAILED;
            case "CANCELLED", "CANCELED" -> AttemptStatus.CANCELLED;
            case "PROCESSING", "PENDING" -> AttemptStatus.PROCESSING;
            default -> AttemptStatus.FAILED;
        };
    }
}
