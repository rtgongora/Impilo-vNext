package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.Map;
import java.util.UUID;

/**
 * Care Pathways, Emergency Protocols, and Inpatient Management.
 *
 * <p>Delegates to PCT and inpatient sovereign services. Read/write paths do not fabricate
 * clinical rows on upstream failure — callers receive {@code 502 BAD_GATEWAY} instead of
 * empty success payloads. Endpoints not yet backed by a service return {@code 501}.</p>
 */
@RestController
@RequestMapping("/internal/v1")
public class CareEmergencyInpatientController {

    private static final Logger log = LoggerFactory.getLogger(CareEmergencyInpatientController.class);

    private final InpatientServiceClient inpatientClient;
    private final PctServiceClient pctClient;

    public CareEmergencyInpatientController(InpatientServiceClient inpatientClient,
                                            PctServiceClient pctClient) {
        this.inpatientClient = inpatientClient;
        this.pctClient = pctClient;
    }

    private static JsonNode requirePayload(JsonNode node, String operation) {
        if (node == null || node.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, operation + ": upstream returned no payload");
        }
        return node;
    }

    private static ResponseStatusException upstreamFailure(String operation, Exception cause) {
        log.warn("{} failed: {}", operation, cause.getMessage());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, operation + " failed", cause);
    }

    // ── Care Plans ──────────────────────────────────────────────────

    @GetMapping("/care-plans")
    public ResponseEntity<Map<String, Object>> listCarePlans(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        try {
            JsonNode pctData = requirePayload(pctClient.listCarePlans(patientId), "PCT listCarePlans");
            return ResponseEntity.ok(Map.of("data", pctData));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT listCarePlans", e);
        }
    }

    @PostMapping("/care-plans")
    public ResponseEntity<Map<String, Object>> createCarePlan(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(pctClient.createCarePlan(body), "PCT createCarePlan");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT createCarePlan", e);
        }
    }

    // ── Fluid Balance ───────────────────────────────────────────────

    @GetMapping("/fluid-balance")
    public ResponseEntity<Map<String, Object>> getFluidBalance(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId,
            @RequestParam(required = false) String date) {
        try {
            JsonNode pctData = requirePayload(pctClient.getFluidBalance(patientId, date), "PCT getFluidBalance");
            return ResponseEntity.ok(Map.of("data", pctData));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT getFluidBalance", e);
        }
    }

    @PostMapping("/fluid-balance")
    public ResponseEntity<Map<String, Object>> recordFluid(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(pctClient.recordFluidBalance(body), "PCT recordFluidBalance");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT recordFluidBalance", e);
        }
    }

    // ── Emergency Activations ───────────────────────────────────────

    @GetMapping("/emergency/activations")
    public ResponseEntity<Map<String, Object>> listActivations(@RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            JsonNode pctData = requirePayload(pctClient.listEmergencyActivations(), "PCT listEmergencyActivations");
            return ResponseEntity.ok(Map.of("data", pctData));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT listEmergencyActivations", e);
        }
    }

    @PostMapping("/emergency/activate")
    public ResponseEntity<Map<String, Object>> activateEmergency(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(pctClient.activateEmergency(body), "PCT activateEmergency");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT activateEmergency", e);
        }
    }

    @PostMapping("/emergency/{id}/action")
    public ResponseEntity<Map<String, Object>> logAction(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode r = requirePayload(pctClient.logEmergencyAction(id.toString(), body), "PCT logEmergencyAction");
            return ResponseEntity.ok(Map.of("data", r));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT logEmergencyAction", e);
        }
    }

    @PostMapping("/emergency/{id}/end")
    public ResponseEntity<Map<String, Object>> endEmergency(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode r = requirePayload(pctClient.endEmergency(id.toString(), body), "PCT endEmergency");
            return ResponseEntity.ok(Map.of("data", r));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT endEmergency", e);
        }
    }

    // ── Resuscitation Records ───────────────────────────────────────

    @PostMapping("/emergency/{activationId}/resuscitation")
    public ResponseEntity<Map<String, Object>> recordResuscitation(
            @PathVariable UUID activationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(
                    pctClient.recordResuscitation(activationId.toString(), body),
                    "PCT recordResuscitation");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT recordResuscitation", e);
        }
    }

    // ── APGAR Scores ────────────────────────────────────────────────

    @PostMapping("/apgar")
    public ResponseEntity<Map<String, Object>> recordApgar(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(pctClient.recordApgar(body), "PCT recordApgar");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT recordApgar", e);
        }
    }

    @GetMapping("/apgar")
    public ResponseEntity<Map<String, Object>> getApgar(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        try {
            JsonNode pctData = requirePayload(pctClient.getApgar(patientId), "PCT getApgar");
            return ResponseEntity.ok(Map.of("data", pctData));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT getApgar", e);
        }
    }

    // ── Early Warning Scores ────────────────────────────────────────

    @PostMapping("/ews")
    public ResponseEntity<Map<String, Object>> recordEWS(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(pctClient.recordEWS(body), "PCT recordEWS");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT recordEWS", e);
        }
    }

    @GetMapping("/ews")
    public ResponseEntity<Map<String, Object>> getEWS(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        try {
            JsonNode pctData = requirePayload(pctClient.getEWS(patientId), "PCT getEWS");
            return ResponseEntity.ok(Map.of("data", pctData));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT getEWS", e);
        }
    }

    // ── Admissions ──────────────────────────────────────────────────

    @PostMapping("/admissions")
    public ResponseEntity<Map<String, Object>> createAdmission(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(inpatientClient.createAdmission(body), "Inpatient createAdmission");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient createAdmission", e);
        }
    }

    @GetMapping("/admissions")
    public ResponseEntity<Map<String, Object>> listAdmissions(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String patientId) {
        try {
            JsonNode data = requirePayload(inpatientClient.listAdmissions(patientId), "Inpatient listAdmissions");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listAdmissions", e);
        }
    }

    // ── Ward Rounds ─────────────────────────────────────────────────

    @PostMapping("/ward-rounds")
    public ResponseEntity<Map<String, Object>> startWardRound(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Ward round start is not wired to inpatient-service in this BFF version.");
    }

    @PostMapping("/ward-rounds/{id}/entries")
    public ResponseEntity<Map<String, Object>> addRoundEntry(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Ward round entries are not wired to inpatient-service in this BFF version.");
    }

    @GetMapping("/ward-rounds")
    public ResponseEntity<Map<String, Object>> listWardRounds(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String wardId) {
        try {
            JsonNode pctData = requirePayload(pctClient.listWardRounds(wardId), "PCT listWardRounds");
            return ResponseEntity.ok(Map.of("data", pctData));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT listWardRounds", e);
        }
    }

    // ── Observation Charts ──────────────────────────────────────────

    @PostMapping("/observations")
    public ResponseEntity<Map<String, Object>> recordObservation(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(pctClient.recordObservation(body), "PCT recordObservation");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT recordObservation", e);
        }
    }

    @GetMapping("/observations")
    public ResponseEntity<Map<String, Object>> getObservations(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        try {
            JsonNode pctData = requirePayload(pctClient.getObservations(patientId), "PCT getObservations");
            return ResponseEntity.ok(Map.of("data", pctData));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT getObservations", e);
        }
    }

    // ── Patient Transfers ───────────────────────────────────────────

    @PostMapping("/transfers")
    public ResponseEntity<Map<String, Object>> requestTransfer(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(inpatientClient.transferPatient(null, body), "Inpatient transferPatient");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient transferPatient", e);
        }
    }

    @PostMapping("/transfers/{id}/accept")
    public ResponseEntity<Map<String, Object>> acceptTransfer(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Transfer accept is not wired to inpatient-service in this BFF version.");
    }

    @GetMapping("/transfers")
    public ResponseEntity<Map<String, Object>> listTransfers(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String patientId) {
        try {
            JsonNode pctData = requirePayload(pctClient.listTransfers(patientId), "PCT listTransfers");
            return ResponseEntity.ok(Map.of("data", pctData));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT listTransfers", e);
        }
    }
}
