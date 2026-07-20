package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;
import zw.gov.mohcc.impilo.experience.recognition.RecognitionCompositionService;

import java.util.Map;

/**
 * Health-worker recognition composition for experience surfaces.
 *
 * <p>GET /internal/v1/experience/recognition/{healthId} — resolves BOTH
 * recognition sources (VARAPI licensed providers + Vashandi workforce) into
 * {recognised, recognitionClass, profession, cadre, licenceStatus}.</p>
 *
 * <p>Display-only: powers the "Recognised Health Provider / Worker" badge
 * chip and the profile benefits card. It must never reorder queues or grant
 * clinical priority. Negative answers are generic (anti-enumeration holds
 * end-to-end because both upstreams are enumeration-resistant).</p>
 */
@RestController
@RequestMapping("/internal/v1/experience/recognition")
public class RecognitionController {

    /** Programme code seeded by coverage V014 (DRAFT until tenant activation). */
    static final String HEALTH_WORKER_PROGRAM_CODE = "SUB-HEALTH-WORKER-RECOGNITION";

    private final RecognitionCompositionService recognitionService;
    private final CoverageServiceClient coverageClient;

    public RecognitionController(RecognitionCompositionService recognitionService,
                                 CoverageServiceClient coverageClient) {
        this.recognitionService = recognitionService;
        this.coverageClient = coverageClient;
    }

    @GetMapping("/{healthId}")
    public ResponseEntity<Map<String, Object>> recognition(
            @PathVariable String healthId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        // PII Wave 0.3: recognition is a self-only badge lookup. The subject is
        // server-authoritative — the JWT-proven actor (X-Actor-ID), never the
        // client-supplied path — so a caller can only read their own recognition
        // and the browser need not carry a subject identifier. The path variable
        // is retained for route compatibility but ignored when an authoritative
        // actor is present.
        String subject = (actorId != null && !actorId.isBlank()) ? actorId : healthId;
        RecognitionCompositionService.RecognitionView view = recognitionService.resolve(subject);
        return ResponseEntity.ok(Map.of(
                "data", view,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    /**
     * Health-worker subsidy self opt-in from the profile benefits card.
     * Coverage performs the recognition check SERVER-SIDE and fail-closed —
     * the BFF only resolves the governed programme (which must be ACTIVE for
     * the tenant) and proxies. 409 when the programme is not activated yet.
     */
    @PostMapping("/subsidy-opt-in")
    public ResponseEntity<Map<String, Object>> subsidyOptIn(
            @RequestBody Map<String, String> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> meta = Map.of("request_id", requestId, "correlation_id", correlationId);
        String healthId = body.get("healthId");
        if (healthId == null || healthId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "HEALTH_ID_REQUIRED"), "meta", meta));
        }
        JsonNode programs = coverageClient.listSubsidies();
        String programId = null;
        if (programs != null && programs.isArray()) {
            for (JsonNode p : programs) {
                if (HEALTH_WORKER_PROGRAM_CODE.equalsIgnoreCase(p.path("programCode").asText(""))) {
                    programId = p.path("id").asText(null);
                    break;
                }
            }
        }
        if (programId == null) {
            // Seeded DRAFT: invisible until a tenant activates it — honest signal, no fake enrolment.
            return ResponseEntity.status(409).body(Map.of(
                    "error", Map.of("code", "PROGRAM_NOT_ACTIVE",
                            "message", "Health worker recognition programme is not active for this tenant"),
                    "meta", meta));
        }
        try {
            JsonNode enrolment = coverageClient.healthWorkerSubsidyOptIn(programId, healthId);
            return ResponseEntity.status(201).body(Map.of(
                    "data", enrolment != null ? enrolment : Map.of(), "meta", meta));
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                    "error", Map.of("code", "OPT_IN_REJECTED",
                            "message", "Opt-in was not accepted (recognition or eligibility check failed)"),
                    "meta", meta));
        }
    }
}
