package zw.gov.mohcc.impilo.mushex.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.api.dto.CreateAttemptRequest;
import zw.gov.mohcc.impilo.mushex.api.dto.CreateIntentRequest;
import zw.gov.mohcc.impilo.mushex.api.dto.RefundRequest;
import zw.gov.mohcc.impilo.mushex.api.dto.ReselectRequest;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentAttemptEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ReceiptEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.RefundEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.RemittanceTokenEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.service.IntentNotFoundException;
import zw.gov.mohcc.impilo.mushex.service.PaymentAttemptService;
import zw.gov.mohcc.impilo.mushex.service.PaymentIntentService;
import zw.gov.mohcc.impilo.mushex.service.RailReselectionService;
import zw.gov.mohcc.impilo.mushex.service.ReceiptService;
import zw.gov.mohcc.impilo.mushex.service.RefundService;
import zw.gov.mohcc.impilo.mushex.service.RemittanceService;
import zw.gov.mohcc.impilo.mushex.service.rail.RailEnforcementException;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionException;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionRequest;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/mushex/v1/payment-intents")
public class PaymentIntentController {

    private final PaymentIntentService paymentIntentService;
    private final RemittanceService remittanceService;
    private final RefundService refundService;
    private final ReceiptService receiptService;
    private final PaymentAttemptService paymentAttemptService;
    private final RailReselectionService railReselectionService;
    private final zw.gov.mohcc.impilo.mushex.domain.repository.PaymentAttemptRepository paymentAttemptRepository;

    public PaymentIntentController(PaymentIntentService paymentIntentService,
                                   RemittanceService remittanceService,
                                   RefundService refundService,
                                   ReceiptService receiptService,
                                   PaymentAttemptService paymentAttemptService,
                                   RailReselectionService railReselectionService,
                                   zw.gov.mohcc.impilo.mushex.domain.repository.PaymentAttemptRepository paymentAttemptRepository) {
        this.paymentIntentService = paymentIntentService;
        this.remittanceService = remittanceService;
        this.refundService = refundService;
        this.receiptService = receiptService;
        this.paymentAttemptService = paymentAttemptService;
        this.railReselectionService = railReselectionService;
        this.paymentAttemptRepository = paymentAttemptRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentIntentEntity>> createIntent(
            @Valid @RequestBody CreateIntentRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        SourceType sourceType = SourceType.valueOf(request.sourceType());
        UUID facilityId = request.facilityId() != null
                ? UUID.fromString(request.facilityId())
                : ctx.facilityId();

        RailSelectionRequest selection = buildSelectionRequest(request, sourceType, facilityId);

        PaymentIntentEntity intent = paymentIntentService.createIntent(
                sourceType,
                request.sourceId(),
                request.amount(),
                request.currency(),
                facilityId,
                request.idempotencyKey(),
                request.metadata(),
                selection
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(intent, correlationId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentIntentEntity>>> listIntents(
            @RequestParam(name = "source_type", required = false) String sourceTypeRaw,
            @RequestParam(name = "source_id", required = false) String sourceId,
            @RequestParam(name = "source_ids", required = false) List<String> sourceIds) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();
        if (sourceTypeRaw == null || sourceTypeRaw.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "source_type is required", 400, correlationId));
        }
        final SourceType sourceType;
        try {
            sourceType = SourceType.valueOf(sourceTypeRaw);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "Invalid source_type: " + sourceTypeRaw, 400, correlationId));
        }
        if (sourceId != null && !sourceId.isBlank()) {
            List<PaymentIntentEntity> rows = paymentIntentService.findByTenantAndSource(
                    ctx.tenantId(), sourceType, sourceId);
            return ResponseEntity.ok(ApiResponse.ok(rows, correlationId));
        }
        Set<String> normalizedIds = sourceIds == null
                ? Set.of()
                : sourceIds.stream().filter(s -> s != null && !s.isBlank()).collect(java.util.stream.Collectors.toSet());
        if (!normalizedIds.isEmpty()) {
            List<PaymentIntentEntity> rows = paymentIntentService.findByTenantAndSourceIds(
                    ctx.tenantId(), sourceType, normalizedIds);
            return ResponseEntity.ok(ApiResponse.ok(rows, correlationId));
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", "Provide source_id or source_ids", 400, correlationId));
    }

    /**
     * Translate the inbound DTO into a {@link RailSelectionRequest}, or {@code null} when
     * the caller did not supply any rail-selection input. A {@code null} return signals to
     * {@code PaymentIntentService} that the deployment-default rail should be used.
     */
    private RailSelectionRequest buildSelectionRequest(CreateIntentRequest request,
                                                       SourceType sourceType,
                                                       UUID facilityId) {
        if (request.preferredRailAdapter() == null
                && request.allowFallback() == null
                && request.directGatewayAllowed() == null) {
            return null;
        }
        return new RailSelectionRequest(
                request.preferredRailAdapter(),
                request.allowFallback(),
                request.directGatewayAllowed(),
                sourceType,
                request.amount(),
                request.currency(),
                facilityId,
                false);
    }

    /**
     * Translate G-4 rail-selection rejections into the standard {@code ApiResponse.error}
     * envelope with the stable error code defined in
     * {@code docs/design/g4-rail-selection-policy.md} §10. Returning HTTP 400 here
     * matches the design (caller error, not server error).
     */
    @ExceptionHandler(RailSelectionException.class)
    public ResponseEntity<ApiResponse<Void>> handleRailSelectionException(RailSelectionException ex) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), 400, correlationId));
    }

    /**
     * Phase 3 — translate {@link RailEnforcementException} (raised at attempt time)
     * into a 422 envelope with the stable error code defined in
     * {@code docs/design/phase-3-attempt-time-rail-enforcement-implementation.md} §3.4.
     */
    @ExceptionHandler(RailEnforcementException.class)
    public ResponseEntity<ApiResponse<Void>> handleRailEnforcementException(RailEnforcementException ex) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), 422, correlationId));
    }

    /**
     * Phase 3 — translate the not-payable signal into a 422 envelope. The error code
     * is stable: {@code INTENT_NOT_PAYABLE}.
     */
    @ExceptionHandler(PaymentAttemptService.AttemptNotPayableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotPayable(PaymentAttemptService.AttemptNotPayableException ex) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error("INTENT_NOT_PAYABLE", ex.getMessage(), 422, correlationId));
    }

    /**
     * Phase 3 follow-on — translate {@link IntentNotFoundException} into a 404 envelope
     * for every endpoint on this controller (previously only the new
     * {@code POST /{id}/attempts} mapping handled this; the GET endpoint returned 500).
     */
    @ExceptionHandler(IntentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntentNotFound(IntentNotFoundException ex) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(IntentNotFoundException.CODE, ex.getMessage(), 404, correlationId));
    }

    /**
     * Phase 3 — initiate a payment attempt against an existing intent. Honours the
     * rail persisted at intent-creation time via {@code RailEnforcement} and the
     * safety gate that prevents real-money adapters from being called unless the
     * operator has explicitly opted them in.
     *
     * <p>See {@code docs/design/phase-3-attempt-time-rail-enforcement-implementation.md}.
     */
    @PostMapping("/{id}/attempts")
    public ResponseEntity<ApiResponse<PaymentAttemptEntity>> initiateAttempt(
            @PathVariable("id") String intentId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String headerKey,
            @RequestBody(required = false) CreateAttemptRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        String bodyKey = request != null ? request.idempotencyKey() : null;
        String idempotencyKey = (bodyKey != null && !bodyKey.isBlank()) ? bodyKey : headerKey;

        PaymentAttemptEntity attempt = paymentAttemptService.initiateAttempt(intentId, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(attempt, correlationId));
    }

    /**
     * Phase 3 follow-on — re-run rail selection for an existing intent. No adapter is
     * called and no money moves; only {@code metadata.rail_selection} is updated and an
     * {@code ATTEMPT_RESELECTED} outbox event is emitted. The previous selection is
     * preserved on {@code metadata.rail_selection.history[]}.
     *
     * <p>See {@code docs/design/phase-3-attempt-time-rail-enforcement-implementation.md} §11.
     */
    @PostMapping("/{id}/attempts/reselect")
    public ResponseEntity<ApiResponse<PaymentIntentEntity>> reselect(
            @PathVariable("id") String intentId,
            @RequestBody(required = false) ReselectRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();
        String reason = request != null ? request.reason() : null;
        PaymentIntentEntity intent = railReselectionService.reselect(intentId, reason);
        return ResponseEntity.ok(ApiResponse.ok(intent, correlationId));
    }

    /**
     * Phase 3 follow-on — list attempts persisted against an intent. Read-only.
     * Empty list for unknown intents; the GET-by-id endpoint above is the canonical
     * way to detect "intent not found" via the 404 mapping.
     */
    @GetMapping("/{id}/attempts")
    public ResponseEntity<ApiResponse<List<PaymentAttemptEntity>>> listAttempts(
            @PathVariable("id") String intentId) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();
        // Surface a 404 here too, even though the underlying repo would happily return [].
        // Operators reading the list endpoint expect 404 to mean "intent does not exist"
        // as opposed to "intent exists but has no attempts yet".
        paymentIntentService.getIntent(intentId);
        List<PaymentAttemptEntity> attempts = paymentAttemptRepository.findByIntentIdOrderByRequestedAtAsc(intentId);
        return ResponseEntity.ok(ApiResponse.ok(attempts, correlationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentIntentEntity>> getIntent(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        PaymentIntentEntity intent = paymentIntentService.getIntent(id);

        return ResponseEntity.ok(ApiResponse.ok(intent, correlationId));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<PaymentIntentEntity>> cancelIntent(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        PaymentIntentEntity intent = paymentIntentService.cancelIntent(id);

        return ResponseEntity.ok(ApiResponse.ok(intent, correlationId));
    }

    @PostMapping("/{id}/issue-remittance-slip")
    public ResponseEntity<ApiResponse<RemittanceTokenEntity>> issueRemittanceSlip(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        RemittanceTokenEntity token = remittanceService.issueRemittanceSlip(id);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(token, correlationId));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<RefundEntity>> refund(
            @PathVariable String id,
            @Valid @RequestBody RefundRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        RefundEntity refund = refundService.requestRefund(id, request.amount(), request.reason());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(refund, correlationId));
    }

    @GetMapping("/{id}/receipts")
    public ResponseEntity<ApiResponse<List<ReceiptEntity>>> getReceipts(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        List<ReceiptEntity> receipts = receiptService.getReceiptsByIntentId(id);

        return ResponseEntity.ok(ApiResponse.ok(receipts, correlationId));
    }
}
