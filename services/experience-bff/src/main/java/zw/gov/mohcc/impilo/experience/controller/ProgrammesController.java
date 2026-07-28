package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HIV and TB care programmes, as the experience layer surfaces them (W3).
 *
 * <ul>
 *   <li>GET  /internal/v1/programmes?patient_id=&amp;open_only= — the patient's programme enrolments</li>
 *   <li>POST /internal/v1/programmes — enrol in a programme</li>
 *   <li>GET  /internal/v1/programmes/{id} · POST {id}/status · POST {id}/exit</li>
 *   <li>GET  /internal/v1/programmes/{id}/regimens · POST {id}/regimens</li>
 *   <li>POST /internal/v1/programmes/{programme}/guidance — governed DAK decision support</li>
 * </ul>
 *
 * <p>pct-service owns the enrolment and regimen record ({@code /v1/programme-enrolments}); CKP owns
 * the governed guidance. This controller composes them and never turns a failed read into an empty
 * one: "not enrolled in any programme" and "the programme list could not be read" are different
 * clinical claims, and only the first may be produced by a call that succeeded.</p>
 *
 * <p><strong>Confidentiality.</strong> HIV enrolments are flagged {@code confidential} by pct
 * (derived from the programme); the surface uses that to present HIV data on the confidential lane.
 * The SPECIALLY_PROTECTED enforcement that will filter it ships in SHADOW — see the pack deliverable
 * for the stated ENFORCE gap. This layer forwards the flag; it does not itself enforce.</p>
 */
@RestController
@RequestMapping("/internal/v1/programmes")
public class ProgrammesController {

    private static final Logger log = LoggerFactory.getLogger(ProgrammesController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PctServiceClient pct;
    private final ClinicalKnowledgePlatformClient ckp;

    public ProgrammesController(PctServiceClient pct, ClinicalKnowledgePlatformClient ckp) {
        this.pct = pct;
        this.ckp = ckp;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(name = "open_only", defaultValue = "false") boolean openOnly) {
        if (patientId == null || patientId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "patient_id_required",
                    "message", "Programme enrolments can only be read for a named patient.",
                    "meta", meta(requestId, correlationId)));
        }
        try {
            JsonNode data = pct.listProgrammeEnrolments(patientId, openOnly);
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            // An empty 200 here reads as "this patient is in no HIV or TB programme", an affirmative
            // and potentially false clinical claim. Fail loudly.
            log.error("PCT listProgrammeEnrolments failed for patient={}: {}", patientId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "programme_list_unavailable",
                    "message", "Programme enrolments could not be retrieved. Do not treat this as the "
                               + "patient being in no programme.",
                    "meta", meta(requestId, correlationId)));
        }
    }

    /**
     * The chronic-disease register for one programme at one facility (brief.md §8).
     *
     * <p>Declared before {@code /{enrolmentId}} so "register" is not read as an id.</p>
     *
     * <p>pct refuses this with 403 for the confidential programmes. That refusal is passed through as
     * a refusal rather than flattened into an empty list — an empty HIV register on screen reads as
     * "nobody here is in HIV care", which is false and reassuring at the same time.</p>
     */
    @GetMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam("programme") String programme,
            @RequestParam(required = false, name = "facility_id") String facilityId,
            @RequestParam(required = false, name = "status") List<String> statuses) {
        try {
            JsonNode data = pct.programmeRegister(programme, facilityId, statuses);
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (HttpClientErrorException.Forbidden e) {
            // The confidential-lane refusal. Preserved as 403 with its reason, because the reason is
            // the point: the list exists, and the control that would protect it does not.
            log.info("Register refused for confidential programme={}", programme);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "confidential_register_not_served",
                    "message", "A patient-level register is not served for this programme: it would "
                               + "name every person on it as having the condition, and the "
                               + "classification that would restrict that list is not yet enforcing. "
                               + "This is not a statement that the register is empty.",
                    "programme", programme,
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.error("PCT programmeRegister failed programme={} facility={}: {}",
                    programme, facilityId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "register_unavailable",
                    "message", "The register could not be retrieved. Do not treat this as nobody "
                               + "being on it.",
                    "meta", meta(requestId, correlationId)));
        }
    }

    @GetMapping("/{enrolmentId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String enrolmentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(Map.of(
                    "data", pct.getProgrammeEnrolment(enrolmentId), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.error("PCT getProgrammeEnrolment failed id={}: {}", enrolmentId, e.getMessage());
            return gatewayError(requestId, correlationId, "programme_unavailable",
                    "The programme enrolment could not be read.");
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> enrol(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = pct.enrolProgramme(normaliseEnrol(body));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", created, "meta", meta(requestId, correlationId)));
        } catch (HttpClientErrorException.Conflict conflict) {
            // The patient already has an active enrolment in this programme. A question with one
            // answer (exit the existing one first), not a failure to swallow.
            log.info("PCT reported a duplicate programme enrolment");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(passthroughConflict(conflict, requestId, correlationId));
        } catch (Exception e) {
            log.error("PCT enrolProgramme failed: {}", e.getMessage());
            return gatewayError(requestId, correlationId, "programme_not_enrolled",
                    "The enrolment was not saved and is not on the patient's record. Record it again.");
        }
    }

    @PostMapping("/{enrolmentId}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String enrolmentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(Map.of(
                    "data", pct.updateProgrammeStatus(enrolmentId, body), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.error("PCT updateProgrammeStatus failed id={}: {}", enrolmentId, e.getMessage());
            return gatewayError(requestId, correlationId, "programme_status_not_updated",
                    "The programme status was not changed.");
        }
    }

    @PostMapping("/{enrolmentId}/exit")
    public ResponseEntity<Map<String, Object>> exit(
            @PathVariable String enrolmentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(Map.of(
                    "data", pct.exitProgramme(enrolmentId, body), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.error("PCT exitProgramme failed id={}: {}", enrolmentId, e.getMessage());
            return gatewayError(requestId, correlationId, "programme_not_exited",
                    "The programme was not exited and remains active on the patient's record.");
        }
    }

    @GetMapping("/{enrolmentId}/regimens")
    public ResponseEntity<Map<String, Object>> regimens(
            @PathVariable String enrolmentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(Map.of(
                    "data", pct.listProgrammeRegimens(enrolmentId), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            // An empty regimen list read as "on no treatment" is dangerous for a patient who is.
            log.error("PCT listProgrammeRegimens failed id={}: {}", enrolmentId, e.getMessage());
            return gatewayError(requestId, correlationId, "regimens_unavailable",
                    "The treatment regimens could not be read. Do not treat this as the patient being "
                    + "on no treatment.");
        }
    }

    @PostMapping("/{enrolmentId}/regimens")
    public ResponseEntity<Map<String, Object>> startRegimen(
            @PathVariable String enrolmentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", pct.startProgrammeRegimen(enrolmentId, body), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.error("PCT startProgrammeRegimen failed id={}: {}", enrolmentId, e.getMessage());
            return gatewayError(requestId, correlationId, "regimen_not_started",
                    "The regimen was not recorded and is not on the patient's record.");
        }
    }

    /**
     * Governed programme decision support. Advisory — the CKP result carries what fired, what could
     * not be assessed, and its provenance. Forwards the caller's facts unchanged.
     */
    @PostMapping("/{programme}/guidance")
    public ResponseEntity<Map<String, Object>> guidance(
            @PathVariable String programme,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> facts) {
        try {
            return ResponseEntity.ok(Map.of(
                    "data", ckp.programmeGuidance(programme, facts), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            // Guidance is advisory. It must fail visibly rather than return an empty alert list that
            // reads as "nothing to flag".
            log.error("CKP programmeGuidance failed programme={}: {}", programme, e.getMessage());
            return gatewayError(requestId, correlationId, "guidance_unavailable",
                    "Programme guidance could not be evaluated. Do not treat the absence of alerts as "
                    + "an all-clear.");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * The UI names the person {@code patientId}; pct's programme record keys on {@code subject_cpid}.
     * Everything else pct reads in either spelling, so only that one key is translated. The rest of
     * the body is forwarded unchanged so a field the UI adds later is not silently dropped here.
     */
    private static Map<String, Object> normaliseEnrol(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>(body);
        Object patient = out.remove("patientId");
        if (patient == null) patient = out.remove("patient_id");
        if (patient != null && !out.containsKey("subject_cpid") && !out.containsKey("subjectCpid")) {
            out.put("subject_cpid", patient);
        }
        return out;
    }

    private static Map<String, Object> passthroughConflict(
            HttpClientErrorException conflict, String requestId, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "programme_already_enrolled");
        try {
            JsonNode upstream = MAPPER.readTree(conflict.getResponseBodyAsString());
            body.put("message", upstream.hasNonNull("message")
                    ? upstream.get("message").asText()
                    : "The patient is already enrolled in this programme.");
            if (upstream.has("active_enrolment_id")) {
                body.put("active_enrolment_id", upstream.get("active_enrolment_id").asText());
            }
            if (upstream.has("programme")) {
                body.put("programme", upstream.get("programme").asText());
            }
        } catch (Exception ignored) {
            body.put("message", "The patient is already enrolled in this programme.");
        }
        body.put("meta", meta(requestId, correlationId));
        return body;
    }

    private static ResponseEntity<Map<String, Object>> gatewayError(
            String requestId, String correlationId, String error, String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", error, "message", message, "meta", meta(requestId, correlationId)));
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        return Map.of("request_id", requestId, "correlation_id", correlationId);
    }
}
