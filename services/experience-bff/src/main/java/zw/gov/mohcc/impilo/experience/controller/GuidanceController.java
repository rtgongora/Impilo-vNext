package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoConsentServiceClient;

import java.util.Map;

/**
 * Guidance BFF Controller — Health OS §12 (Knowledge), §13 (Conversational).
 *
 * <p>Bridges the Experience UI to the guidance-service backend which provides
 * knowledge retrieval, conversational guidance, reminders, education, and
 * federated search. All endpoints proxy to the real guidance-service.</p>
 *
 * @see GuidanceServiceClient
 */
@RestController
@RequestMapping("/internal/v1/guidance")
public class GuidanceController {

    private static final Logger log = LoggerFactory.getLogger(GuidanceController.class);

    private final GuidanceServiceClient guidanceClient;
    private final TshepoConsentServiceClient consentClient;

    public GuidanceController(GuidanceServiceClient guidanceClient, TshepoConsentServiceClient consentClient) {
        this.guidanceClient = guidanceClient;
        this.consentClient = consentClient;
    }

    /** POST /ask — conversational guidance (question → response). */
    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> askGuidance(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            String question = (String) body.getOrDefault("question", "");
            boolean personalized = Boolean.TRUE.equals(body.get("personalized"));
            JsonNode result = guidanceClient.ask(question, personalized);
            return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
        } catch (Exception e) {
            log.error("Guidance ask failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", Map.of(
                    "response", "The guidance service is temporarily unavailable. Please try again.",
                    "confidence", 0.0,
                    "sources", new Object[0],
                    "followUpPrompts", new Object[0],
                    "personalized", false
            )));
        }
    }

    /** GET /reminders — active reminders for the current user. */
    @GetMapping("/reminders")
    public ResponseEntity<Map<String, Object>> getReminders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode result = guidanceClient.getReminders();
            return ResponseEntity.ok(Map.of("data", result != null ? result : new Object[0]));
        } catch (Exception e) {
            log.error("Reminders fetch failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0]));
        }
    }

    /** GET /education — health education content for user context. */
    @GetMapping("/education")
    public ResponseEntity<Map<String, Object>> getEducation(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestParam(defaultValue = "all") String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode result = guidanceClient.getEducation(domain, page, size);
            return ResponseEntity.ok(Map.of("data", result != null ? result : new Object[0]));
        } catch (Exception e) {
            log.error("Education fetch failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0]));
        }
    }

    /** GET /search — federated search across knowledge domains. */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestParam String q,
            @RequestParam(defaultValue = "all") String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode result = guidanceClient.search(q, domain, page, size);
            return ResponseEntity.ok(Map.of("data", result != null ? result : new Object[0]));
        } catch (Exception e) {
            log.error("Guidance search failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0]));
        }
    }

    /** GET /consent-status — check if user has opted into personalized guidance. */
    @GetMapping("/consent-status")
    public ResponseEntity<Map<String, Object>> consentStatus(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.SUBJECT_ID) String subjectId) {
        try {
            JsonNode consents = consentClient.listConsents(subjectId, "ACTIVE", 0, 1);
            boolean hasConsent = consents != null && consents.isArray() && !consents.isEmpty();
            return ResponseEntity.ok(Map.of("data", Map.of("guidanceConsent", hasConsent)));
        } catch (Exception e) {
            log.warn("Consent check failed for subject {}: {}", subjectId, e.getMessage());
            return ResponseEntity.ok(Map.of("data", Map.of("guidanceConsent", true)));
        }
    }
}
