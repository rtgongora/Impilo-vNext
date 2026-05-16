package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.service.CoreTransactionCompositionService;

import java.util.Map;

@RestController
@RequestMapping({"/internal/v1/core-transactions", "/experience/core-transactions"})
public class CoreTransactionController {

    private static final Logger log = LoggerFactory.getLogger(CoreTransactionController.class);

    private final CoreTransactionCompositionService compositionService;

    public CoreTransactionController(CoreTransactionCompositionService compositionService) {
        this.compositionService = compositionService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listCoreTransactions(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String type,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ObjectNode data = compositionService.listCoreTransactions(state, type);
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception ex) {
            log.error("Core transaction list failed", ex);
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of(
                            "code", "CORE_TRANSACTION_UPSTREAM_UNAVAILABLE",
                            "message", "Failed to compose core transactions from upstream services",
                            "details", ex.getMessage(),
                            "request_id", requestId,
                            "correlation_id", correlationId
                    )
            ));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCoreTransaction(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = compositionService.createCoreTransaction(body);
            return ResponseEntity.status(201).body(Map.of(
                    "data", created != null ? created : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception ex) {
            log.error("Core transaction create failed", ex);
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of(
                            "code", "CORE_TRANSACTION_CREATE_FAILED",
                            "message", "Failed to create core transaction",
                            "details", ex.getMessage(),
                            "request_id", requestId,
                            "correlation_id", correlationId
                    )
            ));
        }
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<Map<String, Object>> getCoreTransaction(
            @PathVariable String transactionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ObjectNode view = compositionService.getCoreTransaction(transactionId);
            if (view == null) {
                return ResponseEntity.status(404).body(Map.of(
                        "error", Map.of(
                                "code", "CORE_TRANSACTION_NOT_FOUND",
                                "message", "No core transaction found for id " + transactionId,
                                "request_id", requestId,
                                "correlation_id", correlationId
                        )
                ));
            }
            return ResponseEntity.ok(Map.of(
                    "data", view,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception ex) {
            log.error("Core transaction get failed", ex);
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of(
                            "code", "CORE_TRANSACTION_UPSTREAM_UNAVAILABLE",
                            "message", "Failed to compose core transaction",
                            "details", ex.getMessage(),
                            "request_id", requestId,
                            "correlation_id", correlationId
                    )
            ));
        }
    }

    @PostMapping("/{transactionId}/actions/{actionCode}")
    public ResponseEntity<Map<String, Object>> applyAction(
            @PathVariable String transactionId,
            @PathVariable String actionCode,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode actionResult = compositionService.applyAction(
                    transactionId,
                    actionCode,
                    body == null ? Map.of() : body
            );
            return ResponseEntity.status(202).body(Map.of(
                    "data", actionResult != null ? actionResult : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception ex) {
            log.error("Core transaction action failed", ex);
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of(
                            "code", "CORE_TRANSACTION_ACTION_FAILED",
                            "message", "Failed to apply action " + actionCode + " on transaction " + transactionId,
                            "details", ex.getMessage(),
                            "request_id", requestId,
                            "correlation_id", correlationId
                    )
            ));
        }
    }

    @GetMapping("/{transactionId}/timeline")
    public ResponseEntity<Map<String, Object>> getTimeline(
            @PathVariable String transactionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ArrayNode timeline = compositionService.getTimeline(transactionId);
            return ResponseEntity.ok(Map.of(
                    "data", Map.of("timeline", timeline),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception ex) {
            log.error("Core transaction timeline failed", ex);
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of(
                            "code", "CORE_TRANSACTION_TIMELINE_FAILED",
                            "message", "Failed to compose timeline",
                            "details", ex.getMessage(),
                            "request_id", requestId,
                            "correlation_id", correlationId
                    )
            ));
        }
    }

    @GetMapping("/{transactionId}/next-actions")
    public ResponseEntity<Map<String, Object>> getNextActions(
            @PathVariable String transactionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ArrayNode nextActions = compositionService.getNextActions(transactionId);
            return ResponseEntity.ok(Map.of(
                    "data", Map.of("nextActions", nextActions),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception ex) {
            log.error("Core transaction next-actions failed", ex);
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of(
                            "code", "CORE_TRANSACTION_NEXT_ACTIONS_FAILED",
                            "message", "Failed to compose next actions",
                            "details", ex.getMessage(),
                            "request_id", requestId,
                            "correlation_id", correlationId
                    )
            ));
        }
    }

    @GetMapping("/{transactionId}/journeys")
    public ResponseEntity<Map<String, Object>> getJourneys(
            @PathVariable String transactionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ObjectNode journeys = compositionService.getJourneys(transactionId);
            return ResponseEntity.ok(Map.of(
                    "data", Map.of("journeys", journeys),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception ex) {
            log.error("Core transaction journeys failed", ex);
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of(
                            "code", "CORE_TRANSACTION_JOURNEYS_FAILED",
                            "message", "Failed to compose journey views",
                            "details", ex.getMessage(),
                            "request_id", requestId,
                            "correlation_id", correlationId
                    )
            ));
        }
    }

    @GetMapping("/{transactionId}/nompilo")
    public ResponseEntity<Map<String, Object>> getNompilo(
            @PathVariable String transactionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ObjectNode nompilo = compositionService.getNompilo(transactionId);
            return ResponseEntity.ok(Map.of(
                    "data", Map.of("nompilo", nompilo),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception ex) {
            log.error("Core transaction nompilo failed", ex);
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of(
                            "code", "CORE_TRANSACTION_NOMPILO_FAILED",
                            "message", "Failed to compose Nompilo companion context",
                            "details", ex.getMessage(),
                            "request_id", requestId,
                            "correlation_id", correlationId
                    )
            ));
        }
    }

    @PostMapping("/{transactionId}/feedback")
    public ResponseEntity<Map<String, Object>> submitFeedback(
            @PathVariable String transactionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            ObjectNode feedback = compositionService.submitFeedback(transactionId, body);
            return ResponseEntity.status(202).body(Map.of(
                    "data", feedback,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception ex) {
            log.error("Core transaction feedback failed", ex);
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of(
                            "code", "CORE_TRANSACTION_FEEDBACK_FAILED",
                            "message", "Failed to submit core transaction feedback",
                            "details", ex.getMessage(),
                            "request_id", requestId,
                            "correlation_id", correlationId
                    )
            ));
        }
    }

    @PostMapping("/{transactionId}/nompilo/handoff")
    public ResponseEntity<Map<String, Object>> requestNompiloHandoff(
            @PathVariable String transactionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            ObjectNode handoff = compositionService.requestNompiloHandoff(transactionId, body);
            return ResponseEntity.status(202).body(Map.of(
                    "data", handoff,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception ex) {
            log.error("Core transaction nompilo handoff failed", ex);
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of(
                            "code", "CORE_TRANSACTION_HANDOFF_FAILED",
                            "message", "Failed to request Nompilo handoff",
                            "details", ex.getMessage(),
                            "request_id", requestId,
                            "correlation_id", correlationId
                    )
            ));
        }
    }

    @PostMapping("/{transactionId}/nompilo/command")
    public ResponseEntity<Map<String, Object>> executeNompiloCommand(
            @PathVariable String transactionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            ObjectNode commandResult = compositionService.executeNompiloCommand(transactionId, body);
            return ResponseEntity.status(202).body(Map.of(
                    "data", commandResult,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception ex) {
            log.error("Core transaction nompilo command failed", ex);
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of(
                            "code", "CORE_TRANSACTION_NOMPILO_COMMAND_FAILED",
                            "message", "Failed to execute Nompilo command",
                            "details", ex.getMessage(),
                            "request_id", requestId,
                            "correlation_id", correlationId
                    )
            ));
        }
    }
}
