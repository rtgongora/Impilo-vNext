package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Medication reconciliation (PCT V107) — read proxy for clerking adherence findings.
 */
@RestController
@RequestMapping("/internal/v1/medication-reconciliations")
public class MedicationReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(MedicationReconciliationController.class);

    private final PctServiceClient pctClient;

    public MedicationReconciliationController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        try {
            JsonNode data = pctClient.listMedicationReconciliations(subjectCpid);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("PCT listMedicationReconciliations failed for subject={}: {}", subjectCpid, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "medication_reconciliation_unavailable",
                    "message", "Medication reconciliation could not be retrieved. Do not treat this "
                            + "as a completed or clean reconciliation.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/{reconciliationId}/items")
    public ResponseEntity<Map<String, Object>> items(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable UUID reconciliationId) {
        try {
            JsonNode data = pctClient.listMedicationReconciliationItems(reconciliationId);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("PCT listMedicationReconciliationItems failed for id={}: {}", reconciliationId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "medication_reconciliation_items_unavailable",
                    "message", "Reconciliation findings could not be retrieved. Do not invent adherence.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
