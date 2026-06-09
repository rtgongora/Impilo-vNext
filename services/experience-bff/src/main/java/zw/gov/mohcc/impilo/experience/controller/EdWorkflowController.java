package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Full ED / casualty workflow — sovereign state in pct-service; BFF orchestrates
 * pathway sessions, clinical paging (notification + support tickets), and CKP reference.
 */
@RestController
@RequestMapping("/internal/v1/ed")
public class EdWorkflowController {

    private static final Logger log = LoggerFactory.getLogger(EdWorkflowController.class);

    private final PctServiceClient pctClient;
    private final ClinicalKnowledgePlatformClient ckpClient;
    private final NotificationServiceClient notificationClient;
    private final SupportServiceClient supportClient;

    public EdWorkflowController(PctServiceClient pctClient,
                                ClinicalKnowledgePlatformClient ckpClient,
                                NotificationServiceClient notificationClient,
                                SupportServiceClient supportClient) {
        this.pctClient = pctClient;
        this.ckpClient = ckpClient;
        this.notificationClient = notificationClient;
        this.supportClient = supportClient;
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

    @PostMapping("/visits")
    public ResponseEntity<Map<String, Object>> openVisit(@RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(pctClient.openEdVisit(body), "PCT openEdVisit");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT openEdVisit", e);
        }
    }

    @GetMapping("/visits")
    public ResponseEntity<Map<String, Object>> listVisits(
            @RequestParam(required = false) UUID facilityId,
            @RequestParam(required = false) String status) {
        try {
            JsonNode data = requirePayload(pctClient.listEdVisits(facilityId, status), "PCT listEdVisits");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT listEdVisits", e);
        }
    }

    @GetMapping("/visits/{id}")
    public ResponseEntity<Map<String, Object>> getVisit(@PathVariable UUID id) {
        try {
            JsonNode data = requirePayload(pctClient.getEdVisit(id.toString()), "PCT getEdVisit");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT getEdVisit", e);
        }
    }

    @GetMapping("/triage/discriminators")
    public ResponseEntity<Map<String, Object>> triageDiscriminators() {
        try {
            JsonNode data = requirePayload(pctClient.edTriageDiscriminators(), "PCT edTriageDiscriminators");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT edTriageDiscriminators", e);
        }
    }

    @PostMapping("/triage/score")
    public ResponseEntity<Map<String, Object>> scoreTriage(@RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(pctClient.edTriageScore(body), "PCT edTriageScore");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT edTriageScore", e);
        }
    }

    @PostMapping("/visits/{id}/triage")
    public ResponseEntity<Map<String, Object>> triage(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return proxyVisitPost(id, "/triage", body, "PCT edTriage");
    }

    @PostMapping("/visits/{id}/zone")
    public ResponseEntity<Map<String, Object>> zone(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return proxyVisitPost(id, "/zone", body, "PCT edZone");
    }

    @PostMapping("/visits/{id}/encounter")
    public ResponseEntity<Map<String, Object>> startEncounter(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return proxyVisitPost(id, "/encounter", body != null ? body : Map.of(), "PCT edEncounter");
    }

    @PostMapping("/visits/{id}/protocol")
    public ResponseEntity<Map<String, Object>> bindProtocol(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> enriched = new LinkedHashMap<>(body);
            if (!enriched.containsKey("pathwaySessionId") && enriched.get("pathwayRef") != null) {
                Map<String, Object> sessionBody = new LinkedHashMap<>();
                sessionBody.put("pathway_id", enriched.get("pathwayRef"));
                if (enriched.get("patientCpid") != null) sessionBody.put("patient_id", enriched.get("patientCpid"));
                sessionBody.put("encounter_id", enriched.get("encounterId"));
                try {
                    JsonNode session = ckpClient.startPathwaySession(sessionBody);
                    if (session != null && session.has("id")) {
                        enriched.put("pathwaySessionId", session.get("id").asText());
                    } else if (session != null && session.has("session_id")) {
                        enriched.put("pathwaySessionId", session.get("session_id").asText());
                    }
                } catch (Exception ex) {
                    log.warn("CKP pathway session start failed (non-blocking): {}", ex.getMessage());
                }
            }
            JsonNode data = requirePayload(pctClient.edVisitPost(id.toString(), "/protocol", enriched), "PCT edProtocol");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT edProtocol", e);
        }
    }

    @GetMapping("/visits/{id}/protocol-suggestions")
    public ResponseEntity<Map<String, Object>> protocolSuggestions(@PathVariable UUID id) {
        try {
            JsonNode data = requirePayload(pctClient.edProtocolSuggestions(id.toString()), "PCT edProtocolSuggestions");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT edProtocolSuggestions", e);
        }
    }

    @PostMapping("/visits/{id}/trauma/activate")
    public ResponseEntity<Map<String, Object>> activateTrauma(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> response = proxyVisitPost(id, "/trauma/activate", body, "PCT edTraumaActivate");
        maybePageTraumaTeam(id, body);
        return response;
    }

    @PostMapping("/visits/{id}/trauma/survey")
    public ResponseEntity<Map<String, Object>> traumaSurvey(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return proxyVisitPost(id, "/trauma/survey", body, "PCT edTraumaSurvey");
    }

    @PostMapping("/visits/{id}/trauma/end")
    public ResponseEntity<Map<String, Object>> endTrauma(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return proxyVisitPost(id, "/trauma/end", body != null ? body : Map.of(), "PCT edTraumaEnd");
    }

    @PostMapping("/visits/{id}/page")
    public ResponseEntity<Map<String, Object>> pageTeam(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode pending = requirePayload(pctClient.edVisitPost(id.toString(), "/page", body), "PCT edPage");
            return deliverPage(id, pending, body);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT edPage", e);
        }
    }

    @PostMapping("/visits/{id}/disposition")
    public ResponseEntity<Map<String, Object>> disposition(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return proxyVisitPost(id, "/disposition", body, "PCT edDisposition");
    }

    @PostMapping("/emergency-cases")
    public ResponseEntity<Map<String, Object>> openEmergencyCase(@RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(pctClient.openEmergencyCase(body), "PCT openEmergencyCase");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("PCT openEmergencyCase", e);
        }
    }

    @GetMapping("/pathways")
    public ResponseEntity<Map<String, Object>> listEdPathways() {
        try {
            JsonNode data = requirePayload(ckpClient.listPathways(), "CKP listPathways");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("CKP listPathways", e);
        }
    }

    @PostMapping("/pathways/sessions/{sessionId}/advance")
    public ResponseEntity<Map<String, Object>> advancePathway(
            @PathVariable UUID sessionId, @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(ckpClient.advancePathwaySession(sessionId, body), "CKP advancePathway");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("CKP advancePathway", e);
        }
    }

    private ResponseEntity<Map<String, Object>> proxyVisitPost(
            UUID id, String action, Map<String, Object> body, String op) {
        try {
            JsonNode data = requirePayload(pctClient.edVisitPost(id.toString(), action, body), op);
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure(op, e);
        }
    }

    private void maybePageTraumaTeam(UUID visitId, Map<String, Object> body) {
        Map<String, Object> pageBody = new LinkedHashMap<>();
        pageBody.put("pageType", "TRAUMA_TEAM");
        pageBody.put("recipientRole", "TRAUMA_TEAM");
        pageBody.put("message", "Trauma activation Level " + body.getOrDefault("traumaLevel", "2")
                + " — " + body.getOrDefault("mechanism", "unspecified"));
        pageBody.put("traumaLevel", body.get("traumaLevel"));
        pageBody.put("mechanism", body.get("mechanism"));
        try {
            JsonNode pending = pctClient.edVisitPost(visitId.toString(), "/page", pageBody);
            deliverPage(visitId, pending, pageBody);
        } catch (Exception ex) {
            log.warn("Trauma team auto-page failed (non-blocking): {}", ex.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> deliverPage(UUID visitId, JsonNode pending, Map<String, Object> body) {
        String pageId = pending.path("page").path("page_id").asText(null);
        if (pageId == null) pageId = pending.path("page_id").asText(null);

        String pageType = String.valueOf(body.getOrDefault("pageType", "TEAM")).toUpperCase();
        String templateKey = switch (pageType) {
            case "CODE_BLUE" -> "ED_CODE_BLUE";
            case "TRAUMA", "TRAUMA_TEAM" -> "ED_TRAUMA_TEAM";
            case "CONSULT", "STAT_CONSULT" -> "ED_STAT_CONSULT";
            default -> "ED_TEAM_PAGE";
        };

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("message", body.getOrDefault("message", "ED clinical page"));
        variables.put("patientId", body.getOrDefault("patientId", visitId.toString()));
        variables.put("location", body.getOrDefault("location", "ED"));
        variables.put("zone", body.getOrDefault("zone", ""));
        variables.put("teamLeader", body.getOrDefault("teamLeader", ""));
        variables.put("traumaLevel", body.getOrDefault("traumaLevel", ""));
        variables.put("mechanism", body.getOrDefault("mechanism", ""));
        variables.put("recipientRole", body.getOrDefault("recipientRole", "ED_TEAM"));
        variables.put("pageType", pageType);

        String notificationRef = null;
        String supportRef = null;
        try {
            String recipient = "ed:" + body.getOrDefault("recipientRole", "TEAM");
            Map<String, Object> notify = Map.of(
                    "templateKey", templateKey,
                    "channel", "CODE_BLUE".equals(pageType) || pageType.contains("TRAUMA") ? "PUSH" : "SMS",
                    "to", recipient,
                    "variables", variables,
                    "messageKind", "CLINICAL_PAGE");
            JsonNode sent = notificationClient.sendNotification(notify);
            if (sent != null && sent.has("id")) notificationRef = sent.get("id").asText();
        } catch (Exception ex) {
            log.warn("ED page notification failed: {}", ex.getMessage());
        }

        try {
            Map<String, Object> ticket = new LinkedHashMap<>();
            ticket.put("subject", "ED " + pageType + " — " + visitId);
            ticket.put("description", String.valueOf(variables.get("message")));
            ticket.put("category", "CLINICAL_PAGE");
            ticket.put("priority", "STAT");
            JsonNode created = supportClient.createTicket(ticket);
            if (created != null && created.has("id")) supportRef = created.get("id").asText();
        } catch (Exception ex) {
            log.warn("ED support ticket page failed: {}", ex.getMessage());
        }

        if (pageId != null) {
            Map<String, Object> delivered = new LinkedHashMap<>();
            if (notificationRef != null) delivered.put("notificationRef", notificationRef);
            if (supportRef != null) delivered.put("supportTicketRef", supportRef);
            try {
                pctClient.markEdPageDelivered(pageId, delivered);
            } catch (Exception ex) {
                log.warn("Mark ED page delivered failed: {}", ex.getMessage());
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", pending,
                "notification_ref", notificationRef != null ? notificationRef : "",
                "support_ticket_ref", supportRef != null ? supportRef : ""));
    }
}
