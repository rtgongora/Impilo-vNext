package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transfer of care (brief.md §14 / V116). Thin pass-through over pct.
 *
 * <p>Ownership moves only on accept with an {@code accepting_ref}. Do not invent a local fallback
 * that pretends the transfer recorded when pct could not be reached.</p>
 */
@RestController
@RequestMapping("/internal/v1/care-transfers")
public class CareTransfersController {

    private static final Logger log = LoggerFactory.getLogger(CareTransfersController.class);

    private final PctServiceClient pct;

    public CareTransfersController(PctServiceClient pct) {
        this.pct = pct;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientId) {
        try {
            return ok(pct.listCareTransfers(patientId), requestId, correlationId);
        } catch (Exception e) {
            log.error("PCT listCareTransfers failed patient={}: {}", patientId, e.getMessage());
            return error(requestId, correlationId, "care_transfers_unavailable",
                    "Care transfers could not be read. Do not treat this as no transfer having been "
                    + "requested — ownership may already have moved.");
        }
    }

    @GetMapping("/inbox")
    public ResponseEntity<Map<String, Object>> inbox(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam("service") String service) {
        try {
            return ok(pct.careTransferInbox(service), requestId, correlationId);
        } catch (Exception e) {
            log.error("PCT careTransferInbox failed service={}: {}", service, e.getMessage());
            return error(requestId, correlationId, "care_transfer_inbox_unavailable",
                    "The transfer inbox could not be read. Do not treat this as empty.");
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> request(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = pct.requestCareTransfer(body);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.error("PCT requestCareTransfer failed: {}", e.getMessage());
            return error(requestId, correlationId, "care_transfer_not_requested",
                    "The transfer was not requested. Ownership has not moved.");
        }
    }

    @PostMapping("/{transferId}/accept")
    public ResponseEntity<Map<String, Object>> accept(
            @PathVariable String transferId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            return ok(pct.acceptCareTransfer(transferId, body), requestId, correlationId);
        } catch (Exception e) {
            log.error("PCT acceptCareTransfer failed id={}: {}", transferId, e.getMessage());
            return error(requestId, correlationId, "care_transfer_not_accepted",
                    "The transfer was not accepted. Ownership has not moved.");
        }
    }

    @PostMapping("/{transferId}/decline")
    public ResponseEntity<Map<String, Object>> decline(
            @PathVariable String transferId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            return ok(pct.declineCareTransfer(transferId, body), requestId, correlationId);
        } catch (Exception e) {
            log.error("PCT declineCareTransfer failed id={}: {}", transferId, e.getMessage());
            return error(requestId, correlationId, "care_transfer_not_declined",
                    "The decline was not saved. The requesting team is still waiting.");
        }
    }

    private static ResponseEntity<Map<String, Object>> ok(JsonNode data, String requestId, String correlationId) {
        return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
    }

    private static ResponseEntity<Map<String, Object>> error(String requestId, String correlationId,
                                                             String code, String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", code, "message", message, "meta", meta(requestId, correlationId)));
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("request_id", requestId);
        m.put("correlation_id", correlationId);
        return m;
    }
}
