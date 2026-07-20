package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MusheWalletServiceClient;

import java.util.Map;

/**
 * Ops surface for the physical-write card-sync pipeline (B2): drain the pending
 * card-update queue to the NFC-writer on demand. A scheduled tick also drains when
 * enabled; this is the operator's manual trigger + a place to see per-pass counts.
 */
@RestController
@RequestMapping("/internal/v1/admin/card-sync")
public class AdminCardSyncController {

    private static final Logger log = LoggerFactory.getLogger(AdminCardSyncController.class);

    private final MusheWalletServiceClient musheClient;

    public AdminCardSyncController(MusheWalletServiceClient musheClient) {
        this.musheClient = musheClient;
    }

    @PostMapping("/drain")
    public ResponseEntity<Map<String, Object>> drain(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode counts = musheClient.drainCardSyncQueue();
            if (counts == null) {
                return unavailable(requestId, correlationId);
            }
            return ResponseEntity.ok(Map.of(
                    "data", counts,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Card-sync drain failed: {}", e.getMessage());
            return unavailable(requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> unavailable(String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", Map.of("code", "CARD_SYNC_UNAVAILABLE",
                        "message", "The card-sync service is temporarily unavailable."),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
