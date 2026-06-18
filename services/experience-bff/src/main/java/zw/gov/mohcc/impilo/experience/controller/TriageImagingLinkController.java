package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.imaging.ImagingGovernanceService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/encounters/{encounterId}/imaging-links")
public class TriageImagingLinkController {

    private final PctServiceClient pctServiceClient;
    private final TshepoAuditServiceClient tshepoAuditServiceClient;

    public TriageImagingLinkController(PctServiceClient pctServiceClient,
                                       TshepoAuditServiceClient tshepoAuditServiceClient) {
        this.pctServiceClient = pctServiceClient;
        this.tshepoAuditServiceClient = tshepoAuditServiceClient;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createLink(
            HttpServletRequest request,
            @PathVariable long encounterId,
            @RequestBody Map<String, Object> body) {
        JsonNode raw = pctServiceClient.createEncounterImagingLink(encounterId, toPctBody(body));
        audit(request, "TRIAGE_IMAGING_LINK_CREATED", "POST:encounter/imaging-links", encounterId, body);
        return ResponseEntity.ok(wrap(raw));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listLinks(
            HttpServletRequest request,
            @PathVariable long encounterId) {
        JsonNode raw = pctServiceClient.listEncounterImagingLinks(encounterId);
        audit(request, "TRIAGE_IMAGING_LINK_LIST", "GET:encounter/imaging-links", encounterId, Map.of());
        return ResponseEntity.ok(wrap(raw));
    }

    private static Map<String, Object> toPctBody(Map<String, Object> body) {
        Map<String, Object> pct = new LinkedHashMap<>();
        putIfPresent(pct, "governedStudyId", body, "governed_study_id", "governedStudyId");
        putIfPresent(pct, "orosOrderId", body, "oros_order_id", "orosOrderId");
        putIfPresent(pct, "linkType", body, "link_type", "linkType");
        return pct;
    }

    private static void putIfPresent(Map<String, Object> target, String targetKey,
                                     Map<String, Object> source, String... sourceKeys) {
        for (String key : sourceKeys) {
            Object value = source.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                target.put(targetKey, value);
                return;
            }
        }
    }

    private void audit(HttpServletRequest request, String eventType, String action,
                       long encounterId, Map<String, Object> detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", ImagingGovernanceService.parseTenantUuid(request.getHeader(CompanionHeaders.TENANT_ID)));
        body.put("eventType", eventType);
        body.put("actorId", request.getHeader(CompanionHeaders.ACTOR_ID));
        body.put("actorType", request.getHeader(CompanionHeaders.ACTOR_TYPE));
        body.put("resourceType", "ENCOUNTER_IMAGING_LINK");
        body.put("resourceId", String.valueOf(encounterId));
        body.put("action", action);
        body.put("outcome", "SUCCESS");
        body.put("purposeOfUse", request.getHeader(CompanionHeaders.PURPOSE_OF_USE));
        body.put("correlationId", parseCorrelation(request.getHeader(CompanionHeaders.CORRELATION_ID)));
        if (!detail.isEmpty()) {
            body.put("detail", detail);
        }
        tshepoAuditServiceClient.ingestAuditEvent(body);
    }

    private static UUID parseCorrelation(String raw) {
        if (raw == null || raw.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return UUID.randomUUID();
        }
    }

    private static Map<String, Object> wrap(JsonNode raw) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", raw);
        response.put("success", true);
        return response;
    }
}
