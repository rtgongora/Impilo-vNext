package zw.gov.mohcc.impilo.vito.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.vito.core.CardStatus;
import zw.gov.mohcc.impilo.vito.core.RevocationReason;
import zw.gov.mohcc.impilo.vito.core.card.CardLifecycleService;
import zw.gov.mohcc.impilo.vito.persistence.entity.SmartCardEntity;

import java.util.List;
import java.util.UUID;
import java.util.Objects;

/**
 * SMART Card Management API.
 * All mutations are INTERNAL only. External consumers can read card status.
 */
@RestController
@RequestMapping("/v1/cards")
public class CardController {

    private final CardLifecycleService cardService;

    public CardController(CardLifecycleService cardService) {
        this.cardService = cardService;
    }

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<SmartCardEntity>> requestCard(@RequestBody CardRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);

        UUID healthId;
        try {
            healthId = UUID.fromString(Objects.requireNonNull(request.healthId(), "healthId is required"));
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.status(400).body(
                    ApiResponse.error("INVALID_HEALTH_ID",
                            "healthId must be a valid UUID (e.g. b0000000-0000-4000-8000-000000000001)",
                            400, ctx.correlationId().toString()));
        }

        String requestedBy = ctx.actorId() != null ? ctx.actorId() : "system";
        SmartCardEntity card = cardService.requestCard(
                ctx.tenantId(), healthId, request.publicKey(), requestedBy);
        return ResponseEntity.status(201).body(
                ApiResponse.ok(card, ctx.correlationId().toString()));
    }

    @PostMapping("/{cardId}/print")
    public ResponseEntity<ApiResponse<SmartCardEntity>> markPrinted(@PathVariable Long cardId) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);
        SmartCardEntity card = cardService.markPrinted(ctx.tenantId(), cardId);
        return ResponseEntity.ok(ApiResponse.ok(card, ctx.correlationId().toString()));
    }

    @PostMapping("/{cardId}/activate")
    public ResponseEntity<ApiResponse<SmartCardEntity>> activate(@PathVariable Long cardId) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);
        SmartCardEntity card = cardService.activate(ctx.tenantId(), cardId);
        return ResponseEntity.ok(ApiResponse.ok(card, ctx.correlationId().toString()));
    }

    @PostMapping("/{cardId}/inactivate")
    public ResponseEntity<ApiResponse<SmartCardEntity>> inactivate(@PathVariable Long cardId) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);
        SmartCardEntity card = cardService.inactivate(ctx.tenantId(), cardId);
        return ResponseEntity.ok(ApiResponse.ok(card, ctx.correlationId().toString()));
    }

    @PostMapping("/{cardId}/revoke")
    public ResponseEntity<ApiResponse<SmartCardEntity>> revoke(
            @PathVariable Long cardId, @RequestBody RevokeRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);
        SmartCardEntity card = cardService.revoke(ctx.tenantId(), cardId, request.reason());
        return ResponseEntity.ok(ApiResponse.ok(card, ctx.correlationId().toString()));
    }

    @GetMapping("/active/{healthId}")
    public ResponseEntity<ApiResponse<SmartCardEntity>> getActiveCard(@PathVariable UUID healthId) {
        TrustContext ctx = TrustContextHolder.require();
        return cardService.getActiveCard(ctx.tenantId(), healthId)
                .map(card -> ResponseEntity.ok(ApiResponse.ok(card, ctx.correlationId().toString())))
                .orElse(ResponseEntity.status(404).body(
                        ApiResponse.error("NOT_FOUND", "No active card", 404, ctx.correlationId().toString())));
    }

    @GetMapping("/history/{healthId}")
    public ResponseEntity<ApiResponse<List<SmartCardEntity>>> getCardHistory(
            @PathVariable UUID healthId) {
        TrustContext ctx = TrustContextHolder.require();
        List<SmartCardEntity> history = cardService.getCardHistory(ctx.tenantId(), healthId);
        return ResponseEntity.ok(ApiResponse.ok(history, ctx.correlationId().toString()));
    }

    @GetMapping("/by-status/{status}")
    public ResponseEntity<ApiResponse<PagedResponse<SmartCardEntity>>> listByStatus(
            @PathVariable CardStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();
        Page<SmartCardEntity> cards = cardService.listByStatus(
                ctx.tenantId(), status, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(cards.getContent(), page, size, cards.getTotalElements()),
                ctx.correlationId().toString()));
    }

    private void requireInternal(TrustContext ctx) {
        if (ctx.mode() == AccessMode.EXTERNAL) {
            throw new SecurityException("Card management operations are internal only");
        }
    }

    record CardRequest(String healthId, String publicKey) {}
    record RevokeRequest(RevocationReason reason) {}
}
