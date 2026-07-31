package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/clerking/visit-attestations")
public class ClerkingAttestationController {

    private static final Logger log = LoggerFactory.getLogger(ClerkingAttestationController.class);

    private final PctServiceClient pctClient;

    public ClerkingAttestationController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "subject_cpid") String subjectCpid,
            @RequestParam(name = "encounter_id") String encounterId) {
        try {
            JsonNode data = pctClient.listVisitAttestations(subjectCpid, encounterId);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("PCT visit attestations failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "visit_attestations_unavailable",
                    "message", "Visit attestations could not be retrieved. Do not invent confirmation.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> attest(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = pctClient.recordVisitAttestation(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("PCT visit attestation write failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "visit_attestation_write_failed",
                    "message", "Attestation was not saved. Silence was not recorded as confirmation.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
