package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vito.config.StepUpRequired;
import zw.gov.mohcc.impilo.vito.core.CardStatus;
import zw.gov.mohcc.impilo.vito.persistence.entity.*;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.SmartCardRepository;

import zw.gov.mohcc.impilo.vito.core.issuance.IssuanceStateMachineService;
import zw.gov.mohcc.impilo.vito.core.pickup.DelegatedPickupService;
import zw.gov.mohcc.impilo.vito.persistence.repository.IssuanceRequestRepository;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Print job intake — internal only.
 * Receives print job requests and queues them for the card-print-agent.
 *
 * IMPORTANT: This controller does NOT mark the card as PRINTED. That transition
 * happens when card-print-agent confirms via vito.print.audit callback.
 */
@RestController
@RequestMapping("/v1/print")
public class PrintJobController {

    private final SmartCardRepository cardRepo;
    private final EventOutboxRepository outboxRepo;
    private final DelegatedPickupService pickupService;
    private final IssuanceRequestRepository issuanceRepo;
    private final IssuanceStateMachineService issuanceService;

    public PrintJobController(SmartCardRepository cardRepo,
                              EventOutboxRepository outboxRepo,
                              DelegatedPickupService pickupService,
                              IssuanceRequestRepository issuanceRepo,
                              IssuanceStateMachineService issuanceService) {
        this.cardRepo = cardRepo;
        this.outboxRepo = outboxRepo;
        this.pickupService = pickupService;
        this.issuanceRepo = issuanceRepo;
        this.issuanceService = issuanceService;
    }

    /**
     * POST /v1/print/card/job — submit a print job.
     * Validates the card is in REQUESTED state and emits an event for card-print-agent.
     * Card status remains REQUESTED until the agent confirms printing.
     */
    @StepUpRequired(reason = "Printing new cards requires step-up authentication")
    @PostMapping("/card/job")
    public ResponseEntity<Map<String, Object>> submitPrintJob(@RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();

        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(Map.of("error", "INTERNAL_ONLY"));
        }

        UUID tenantId = ctx.tenantId();
        Long cardId = ((Number) body.get("cardId")).longValue();
        String template = (String) body.getOrDefault("template", "STANDARD");

        SmartCardEntity card = cardRepo.findById(cardId)
                .filter(c -> tenantId == null || c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));

        if (card.getStatus() != CardStatus.REQUESTED) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_STATE",
                    "message", "Card must be in REQUESTED state, current: " + card.getStatus()));
        }

        // Emit print job event for card-print-agent
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("PRINT_JOB");
        event.setAggregateId(cardId.toString());
        event.setEventType("vito.print.job.created");
        event.setPayload("{\"cardId\":" + cardId + ",\"template\":\"" + template + "\",\"did\":\"" + card.getDidUri() + "\"}");
        outboxRepo.save(event);

        return ResponseEntity.ok(Map.of(
                "cardId", card.getId(),
                "cardNumber", card.getCardNumber(),
                "status", "PRINT_QUEUED",
                "message", "Print job submitted. Card status will update when agent confirms printing."
        ));
    }

    /**
     * POST /v1/print/card/verify-pickup — verify delegate token + OTP without redeeming.
     */
    @PostMapping("/card/verify-pickup")
    public ResponseEntity<Map<String, Object>> verifyPickup(@RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(Map.of("valid", false, "error", "INTERNAL_ONLY"));
        }

        String pickupToken = textField(body, "pickupToken", "pickup_token");
        String otp = textField(body, "delegateOtp", "delegate_otp", "otp");
        if (pickupToken == null || otp == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "valid", false,
                    "message", "pickupToken and delegateOtp are required"));
        }

        try {
            DelegatedPickupService.PickupVerification verification = pickupService.verifyPickup(pickupToken, otp);
            IssuanceRequestEntity issuance = issuanceRepo.findById(verification.issuanceRequestId())
                    .filter(r -> r.getTenantId().equals(ctx.tenantId()))
                    .orElseThrow(() -> new IllegalArgumentException("Issuance request not found"));
            String cardId = issuance.getCardId() != null ? issuance.getCardId().toString() : "";
            String facilityName = verification.facilityId() != null
                    ? verification.facilityId().toString()
                    : "";
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "delegateName", verification.delegateName(),
                    "facilityName", facilityName,
                    "cardId", cardId,
                    "expiresAt", verification.expiresAt().toString()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "valid", false,
                    "message", ex.getMessage()));
        }
    }

    /**
     * POST /v1/print/card/confirm-handover — redeem pickup and mark issuance delivered.
     */
    @PostMapping("/card/confirm-handover")
    public ResponseEntity<Map<String, Object>> confirmHandover(@RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(Map.of("confirmed", false, "error", "INTERNAL_ONLY"));
        }

        String pickupToken = textField(body, "pickupToken", "pickup_token");
        String otp = textField(body, "delegateOtp", "delegate_otp", "otp");
        if (pickupToken == null || otp == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "confirmed", false,
                    "message", "pickupToken and delegateOtp are required"));
        }

        try {
            DelegatedPickupEntity pickup = pickupService.redeem(pickupToken, otp, ctx.actorId());
            IssuanceRequestEntity issuance = issuanceRepo.findById(pickup.getIssuanceRequestId())
                    .filter(r -> r.getTenantId().equals(ctx.tenantId()))
                    .orElseThrow(() -> new IllegalArgumentException("Issuance request not found"));
            issuanceService.deliver(ctx.tenantId(), issuance.getId());
            OffsetDateTime confirmedAt = OffsetDateTime.now();
            String cardId = issuance.getCardId() != null ? issuance.getCardId().toString() : "";
            return ResponseEntity.ok(Map.of(
                    "confirmed", true,
                    "confirmedAt", confirmedAt.toString(),
                    "cardId", cardId,
                    "message", "Card handover confirmed."));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "confirmed", false,
                    "message", ex.getMessage()));
        }
    }

    private static String textField(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        return null;
    }
}
