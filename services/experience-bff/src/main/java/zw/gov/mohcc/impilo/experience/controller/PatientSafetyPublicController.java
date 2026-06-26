package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PatientSafetyServiceClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public (citizen / caregiver) pharmacovigilance intake. Reachable from the self-service app via the
 * gateway's {@code /v1/public/**} route. Citizen requests are anonymous, so this controller supplies
 * the downstream v1.1 trust headers explicitly: a configured public tenant, generated request/
 * correlation/idempotency ids, and a CITIZEN actor. The patient-safety-service remains the SoR.
 */
@RestController
@RequestMapping("/v1/public/patient-safety")
public class PatientSafetyPublicController {

    private static final Logger log = LoggerFactory.getLogger(PatientSafetyPublicController.class);

    private final PatientSafetyServiceClient client;
    private final String publicTenantId;
    private final String publicPodId;

    public PatientSafetyPublicController(
            PatientSafetyServiceClient client,
            @Value("${impilo.patient-safety.public-tenant-id:00000000-0000-4000-8000-000000000001}") String publicTenantId,
            @Value("${impilo.patient-safety.public-pod-id:national-spine}") String publicPodId) {
        this.client = client;
        this.publicTenantId = publicTenantId;
        this.publicPodId = publicPodId;
    }

    private HttpHeaders systemHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(CompanionHeaders.TENANT_ID, publicTenantId);
        h.set(CompanionHeaders.POD_ID, publicPodId);
        h.set(CompanionHeaders.REQUEST_ID, UUID.randomUUID().toString());
        h.set(CompanionHeaders.CORRELATION_ID, UUID.randomUUID().toString());
        h.set(CompanionHeaders.ACTOR_ID, "citizen-self-service");
        h.set(CompanionHeaders.ACTOR_TYPE, "CITIZEN");
        h.set(CompanionHeaders.PURPOSE_OF_USE, "PHARMACOVIGILANCE");
        h.set(CompanionHeaders.IDEMPOTENCY_KEY, "citizen-" + UUID.randomUUID());
        return h;
    }

    /**
     * Citizen simplified report: creates the report (forcing reporterKind to CITIZEN/CAREGIVER) and
     * submits it in one step, returning the receipt (reference + opened case).
     */
    @PostMapping("/reports")
    public ResponseEntity<Map<String, Object>> submitCitizenReport(@RequestBody Map<String, Object> body) {
        Map<String, Object> payload = new HashMap<>(body);
        Object kind = payload.get("reporterKind");
        if (!"CAREGIVER".equals(kind)) {
            payload.put("reporterKind", "CITIZEN");
        }
        payload.put("sourceContext", "SELF_SERVICE");
        HttpHeaders createHeaders = systemHeaders();
        JsonNode created = client.createReportWithHeaders(payload, createHeaders);
        if (created == null || !created.has("id")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "create_failed",
                    "message", "Could not create the report. Please check the medicine name and try again."));
        }
        String reportId = created.get("id").asText();
        JsonNode submitted = client.submitReportWithHeaders(reportId, systemHeaders());
        return ResponseEntity.status(201).body(Map.of("data", submitted != null ? submitted : created));
    }

    /** External WHO-UMC VigiMobile / VigiFlow eForm + MCAZ channel link-outs (labelled external). */
    @GetMapping("/config/vigimobile-links")
    public ResponseEntity<Map<String, Object>> vigiMobileLinks() {
        try {
            return ResponseEntity.ok(Map.of("data", client.vigiMobileLinks()));
        } catch (Exception e) {
            log.warn("Public VigiMobile links unavailable: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", List.of()));
        }
    }
}
