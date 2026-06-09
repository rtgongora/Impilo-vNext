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
            JsonNode data = requirePayload(inpatientClient.listCarePlans(patientId), "Inpatient listCarePlans");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listCarePlans", e);
        }
    }

    @PostMapping("/care-plans")
    public ResponseEntity<Map<String, Object>> createCarePlan(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(inpatientClient.createCarePlan(body), "Inpatient createCarePlan");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient createCarePlan", e);
        }
    }

    // ── Fluid Balance ───────────────────────────────────────────────

    @GetMapping("/fluid-balance")
    public ResponseEntity<Map<String, Object>> getFluidBalance(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId,
            @RequestParam(required = false) String date) {
        try {
            JsonNode payload = requirePayload(inpatientClient.getFluidBalance(patientId, date), "Inpatient getFluidBalance");
            if (payload.has("data") && payload.has("summary")) {
                return ResponseEntity.ok(Map.of("data", payload.get("data"), "summary", payload.get("summary")));
            }
            return ResponseEntity.ok(Map.of("data", payload));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient getFluidBalance", e);
        }
    }

    @PostMapping("/fluid-balance")
    public ResponseEntity<Map<String, Object>> recordFluid(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(inpatientClient.recordFluidBalance(body), "Inpatient recordFluidBalance");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient recordFluidBalance", e);
        }
    }

    // ── Emergency Activations ───────────────────────────────────────

    @GetMapping("/emergency/activations")
    public ResponseEntity<Map<String, Object>> listActivations(@RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            JsonNode data = requirePayload(inpatientClient.listEmergencyActivations(), "Inpatient listEmergencyActivations");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listEmergencyActivations", e);
        }
    }

    @PostMapping("/emergency/activate")
    public ResponseEntity<Map<String, Object>> activateEmergency(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(inpatientClient.activateEmergency(body), "Inpatient activateEmergency");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient activateEmergency", e);
        }
    }

    @PostMapping("/emergency/{id}/action")
    public ResponseEntity<Map<String, Object>> logAction(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode r = requirePayload(inpatientClient.logEmergencyAction(id.toString(), body), "Inpatient logEmergencyAction");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", r));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient logEmergencyAction", e);
        }
    }

    @PostMapping("/emergency/{id}/end")
    public ResponseEntity<Map<String, Object>> endEmergency(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode r = requirePayload(inpatientClient.endEmergency(id.toString(), body), "Inpatient endEmergency");
            return ResponseEntity.ok(Map.of("data", r));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient endEmergency", e);
        }
    }

    // ── Resuscitation Records ───────────────────────────────────────

    @PostMapping("/emergency/{activationId}/resuscitation")
    public ResponseEntity<Map<String, Object>> recordResuscitation(
            @PathVariable UUID activationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(
                    inpatientClient.recordResuscitation(activationId.toString(), body),
                    "Inpatient recordResuscitation");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient recordResuscitation", e);
        }
    }

    // ── APGAR Scores ────────────────────────────────────────────────

    @PostMapping("/apgar")
    public ResponseEntity<Map<String, Object>> recordApgar(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(inpatientClient.recordApgar(body), "Inpatient recordApgar");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient recordApgar", e);
        }
    }

    @GetMapping("/apgar")
    public ResponseEntity<Map<String, Object>> getApgar(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        try {
            JsonNode data = requirePayload(inpatientClient.getApgar(patientId), "Inpatient getApgar");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient getApgar", e);
        }
    }

    // ── Early Warning Scores ────────────────────────────────────────

    @PostMapping("/ews")
    public ResponseEntity<Map<String, Object>> recordEWS(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(inpatientClient.recordEws(body), "Inpatient recordEWS");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient recordEWS", e);
        }
    }

    @GetMapping("/ews")
    public ResponseEntity<Map<String, Object>> getEWS(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        try {
            JsonNode data = requirePayload(inpatientClient.getEws(patientId), "Inpatient getEWS");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient getEWS", e);
        }
    }

    // ── Admissions ──────────────────────────────────────────────────

    @PostMapping("/admissions")
    public ResponseEntity<Map<String, Object>> createAdmission(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(
                    inpatientClient.createAdmission(InpatientAdmissionNormalizer.normalize(tenantId, body)),
                    "Inpatient createAdmission");
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
        try {
            JsonNode data = requirePayload(inpatientClient.startWardRound(body), "Inpatient startWardRound");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient startWardRound", e);
        }
    }

    @PostMapping("/ward-rounds/{id}/entries")
    public ResponseEntity<Map<String, Object>> addRoundEntry(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(inpatientClient.addWardRoundEntry(id.toString(), body), "Inpatient addWardRoundEntry");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient addWardRoundEntry", e);
        }
    }

    @GetMapping("/ward-rounds")
    public ResponseEntity<Map<String, Object>> listWardRounds(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String wardId) {
        try {
            JsonNode data = requirePayload(inpatientClient.listWardRoundsByWard(wardId), "Inpatient listWardRounds");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listWardRounds", e);
        }
    }

    // ── Observation Charts ──────────────────────────────────────────

    @PostMapping("/observations")
    public ResponseEntity<Map<String, Object>> recordObservation(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(inpatientClient.recordObservation(body), "Inpatient recordObservation");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient recordObservation", e);
        }
    }

    @GetMapping("/observations")
    public ResponseEntity<Map<String, Object>> getObservations(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        try {
            JsonNode data = requirePayload(inpatientClient.getObservations(patientId), "Inpatient getObservations");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient getObservations", e);
        }
    }

    // ── Patient Transfers ───────────────────────────────────────────

    @PostMapping("/transfers")
    public ResponseEntity<Map<String, Object>> requestTransfer(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(inpatientClient.requestTransfer(body), "Inpatient requestTransfer");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient requestTransfer", e);
        }
    }

    @PostMapping("/transfers/{id}/accept")
    public ResponseEntity<Map<String, Object>> acceptTransfer(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(inpatientClient.acceptTransfer(id.toString(), body), "Inpatient acceptTransfer");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient acceptTransfer", e);
        }
    }

    @GetMapping("/transfers")
    public ResponseEntity<Map<String, Object>> listTransfers(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String patientId) {
        try {
            JsonNode data = requirePayload(inpatientClient.listTransfers(patientId), "Inpatient listTransfers");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listTransfers", e);
        }
    }

    // ── Ward charts (web EHR) ───────────────────────────────────────

    @GetMapping("/ward-charts/activity")
    public ResponseEntity<Map<String, Object>> wardChartActivity(@RequestParam String patientId) {
        try {
            JsonNode data = requirePayload(inpatientClient.getWardChartActivity(patientId), "Inpatient wardChartActivity");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (Exception e) {
            throw upstreamFailure("Inpatient wardChartActivity", e);
        }
    }

    @GetMapping("/ward-charts/{chartType}/entries")
    public ResponseEntity<Map<String, Object>> wardChartEntries(@PathVariable String chartType,
                                                                @RequestParam String patientId) {
        try {
            JsonNode data = requirePayload(inpatientClient.getWardChartEntries(chartType, patientId), "Inpatient wardChartEntries");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (Exception e) {
            throw upstreamFailure("Inpatient wardChartEntries", e);
        }
    }

    @PostMapping("/ward-charts/{chartType}/entries")
    public ResponseEntity<Map<String, Object>> recordWardChartEntry(@PathVariable String chartType,
                                                                    @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(inpatientClient.recordWardChartEntry(chartType, body), "Inpatient recordWardChartEntry");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (Exception e) {
            throw upstreamFailure("Inpatient recordWardChartEntry", e);
        }
    }

    // ── Clinical handover / takeover ────────────────────────────────

    @PostMapping("/inpatient/handover")
    public ResponseEntity<Map<String, Object>> submitInpatientHandover(@RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(inpatientClient.submitHandover(body), "Inpatient submitHandover");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (Exception e) {
            throw upstreamFailure("Inpatient submitHandover", e);
        }
    }

    @PostMapping("/inpatient/handover/{id}/takeover")
    public ResponseEntity<Map<String, Object>> acceptInpatientTakeover(@PathVariable UUID id,
                                                                       @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(inpatientClient.acceptTakeover(id.toString(), body), "Inpatient acceptTakeover");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (Exception e) {
            throw upstreamFailure("Inpatient acceptTakeover", e);
        }
    }

    // ── Ward alerts (provider acknowledge) ──────────────────────────

    @GetMapping("/ward-alerts")
    public ResponseEntity<Map<String, Object>> listWardAlerts(@RequestParam String wardId,
                                                              @RequestParam(required = false, defaultValue = "ACTIVE") String status) {
        try {
            JsonNode data = requirePayload(inpatientClient.listWardAlerts(wardId, status), "Inpatient listWardAlerts");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listWardAlerts", e);
        }
    }

    @PostMapping("/ward-alerts/{id}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeWardAlert(@PathVariable UUID id,
                                                                    @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(inpatientClient.acknowledgeWardAlert(id.toString(), body), "Inpatient acknowledgeWardAlert");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (Exception e) {
            throw upstreamFailure("Inpatient acknowledgeWardAlert", e);
        }
    }
}
