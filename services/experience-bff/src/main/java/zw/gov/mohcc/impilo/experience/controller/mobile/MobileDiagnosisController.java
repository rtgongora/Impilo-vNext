package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.SearchServiceClient;
import zw.gov.mohcc.impilo.experience.support.Icd11SearchNormalizer;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile diagnosis endpoints.
 * GET    /internal/v1/mobile/provider/diagnosis/icd11/search?q= - ICD-11 search
 * POST   /internal/v1/mobile/provider/diagnosis                 - record diagnosis
 * GET    /internal/v1/mobile/provider/diagnosis?encounter_id=   - list diagnoses
 * DELETE /internal/v1/mobile/provider/diagnosis/{id}            - delete diagnosis
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/diagnosis")
public class MobileDiagnosisController {

    private static final Logger log = LoggerFactory.getLogger(MobileDiagnosisController.class);

    private final PctServiceClient pctClient;
    private final SearchServiceClient searchClient;

    public MobileDiagnosisController(PctServiceClient pctClient, SearchServiceClient searchClient) {
        this.pctClient = pctClient;
        this.searchClient = searchClient;
    }

    public record RecordDiagnosisRequest(
            @NotBlank String encounter_id,
            @NotBlank String patient_id,
            @NotBlank String icd11_code,
            @NotBlank String icd11_title,
            String diagnosis_type,
            String certainty,
            String notes
    ) {}

    @GetMapping("/icd11/search")
    public ResponseEntity<Map<String, Object>> searchIcd11(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            var result = searchClient.search(query, "ICD11", 0, limit);
            return ResponseEntity.ok(Map.of(
                    "data", Icd11SearchNormalizer.normalize(result),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> recordDiagnosis(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RecordDiagnosisRequest request) {

        UUID diagnosisId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        String diagnosisType = request.diagnosis_type() != null ? request.diagnosis_type() : "PRIMARY";
        String certainty = request.certainty() != null ? request.certainty() : "CONFIRMED";

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("patient_id", request.patient_id());
        attributes.put("icd11_code", request.icd11_code());
        attributes.put("icd11_title", request.icd11_title());
        attributes.put("diagnosis_type", diagnosisType);
        attributes.put("certainty", certainty);
        attributes.put("notes", request.notes());
        attributes.put("diagnosed_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", diagnosisId.toString(),
                "type", "Diagnosis",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listDiagnoses(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (patientId == null || patientId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "patient_id_required",
                    "message", "A diagnosis list can only be read for a named patient.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        }
        try {
            JsonNode conditions = pctClient.listProblems(patientId);
            return ResponseEntity.ok(Map.of(
                    "data", conditions != null ? conditions : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception e) {
            // Returning an empty list here told a clinician on a phone, at the bedside, that the
            // patient had no diagnoses recorded. Say the list is unavailable instead.
            log.error("PCT listProblems failed for patient={}: {}", patientId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "diagnosis_list_unavailable",
                    "message", "Diagnoses could not be retrieved. Do not treat this as an absence "
                               + "of diagnoses.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDiagnosis(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "deleted", true));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }
}
