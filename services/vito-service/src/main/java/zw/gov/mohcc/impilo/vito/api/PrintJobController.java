package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vito.config.StepUpRequired;
import zw.gov.mohcc.impilo.vito.core.CardStatus;
import zw.gov.mohcc.impilo.vito.persistence.entity.*;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.SmartCardRepository;

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

    public PrintJobController(SmartCardRepository cardRepo, EventOutboxRepository outboxRepo) {
        this.cardRepo = cardRepo;
        this.outboxRepo = outboxRepo;
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

        // Validate the card exists and belongs to this tenant
        SmartCardEntity card = cardRepo.findById(cardId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Card not found"));

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
}
