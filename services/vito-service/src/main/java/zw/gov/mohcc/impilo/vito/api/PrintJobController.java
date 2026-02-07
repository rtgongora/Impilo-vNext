package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vito.config.StepUpRequired;
import zw.gov.mohcc.impilo.vito.core.card.CardLifecycleService;
import zw.gov.mohcc.impilo.vito.persistence.entity.*;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.util.*;

/**
 * Print job intake — internal only.
 * Receives print job requests and queues them for the card-print-agent.
 */
@RestController
@RequestMapping("/v1/print")
public class PrintJobController {

    private final CardLifecycleService cardService;
    private final EventOutboxRepository outboxRepo;

    public PrintJobController(CardLifecycleService cardService, EventOutboxRepository outboxRepo) {
        this.cardService = cardService;
        this.outboxRepo = outboxRepo;
    }

    /**
     * POST /v1/print/card/job — submit a print job.
     * Marks the card as ready for printing and emits an event for card-print-agent.
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

        SmartCardEntity card = cardService.markPrinted(tenantId, cardId);

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
                "status", card.getStatus().name(),
                "message", "Print job submitted"
        ));
    }
}
