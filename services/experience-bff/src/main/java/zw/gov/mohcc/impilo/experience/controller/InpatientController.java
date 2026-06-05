package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.service.CoreTransactionCompositionService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Inpatient admissions proxy for the web shell ({@code /internal/v1/inpatient/**}).
 * Delegates to inpatient-service sovereign admissions API.
 */
@RestController
@RequestMapping("/internal/v1/inpatient")
public class InpatientController {

    private static final Logger log = LoggerFactory.getLogger(InpatientController.class);

    private final InpatientServiceClient inpatientClient;

    public InpatientController(InpatientServiceClient inpatientClient) {
        this.inpatientClient = inpatientClient;
    }

    private static JsonNode requirePayload(JsonNode node, String operation) {
        if (node == null || node.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, operation + ": upstream returned no payload");
        }
        return node;
    }

    private static String extractAdmissionRef(JsonNode created) {
        if (created == null || created.isNull()) {
            return null;
        }
        String direct = created.path("admissionRef").asText(null);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        direct = created.path("id").asText(null);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        return created.path("admissionId").asText(null);
    }

    private static ResponseStatusException upstreamFailure(String operation, Exception cause) {
        log.warn("{} failed: {}", operation, cause.getMessage());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, operation + " failed", cause);
    }

    @GetMapping("/admissions")
    public ResponseEntity<Map<String, Object>> listAdmissions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "patient_cpid") String patientCpid,
            @RequestParam(required = false, name = "patientId") String patientId) {
        String cpid = patientCpid != null && !patientCpid.isBlank() ? patientCpid : patientId;
        try {
            JsonNode data = requirePayload(inpatientClient.listAdmissions(cpid), "Inpatient listAdmissions");
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listAdmissions", e);
        }
    }

    @GetMapping("/admissions/{admissionRef}")
    public ResponseEntity<Map<String, Object>> getAdmission(
            @PathVariable String admissionRef,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = requirePayload(inpatientClient.getAdmission(admissionRef), "Inpatient getAdmission");
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient getAdmission", e);
        }
    }

    @PostMapping("/admissions")
    public ResponseEntity<Map<String, Object>> createAdmission(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(inpatientClient.createAdmission(body), "Inpatient createAdmission");
            String admissionRef = extractAdmissionRef(created);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("request_id", requestId);
            meta.put("correlation_id", correlationId);
            if (admissionRef != null) {
                meta.put("admission_ref", admissionRef);
                meta.put("core_transaction_id", CoreTransactionCompositionService.admissionTransactionId(admissionRef));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", created,
                    "meta", meta));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient createAdmission", e);
        }
    }

    @PostMapping("/admissions/{admissionRef}/discharge")
    public ResponseEntity<Map<String, Object>> dischargeAdmission(
            @PathVariable String admissionRef,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(
                    inpatientClient.dischargeAdmission(admissionRef, body),
                    "Inpatient dischargeAdmission");
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient dischargeAdmission", e);
        }
    }

    @PostMapping("/admissions/{admissionRef}/transfer")
    public ResponseEntity<Map<String, Object>> transferAdmission(
            @PathVariable String admissionRef,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(
                    inpatientClient.transferPatient(admissionRef, body),
                    "Inpatient transferPatient");
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient transferPatient", e);
        }
    }

    @GetMapping("/admissions/{admissionRef}/ward-rounds")
    public ResponseEntity<Map<String, Object>> listWardRounds(
            @PathVariable String admissionRef,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = requirePayload(
                    inpatientClient.listWardRounds(admissionRef),
                    "Inpatient listWardRounds");
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listWardRounds", e);
        }
    }
}
