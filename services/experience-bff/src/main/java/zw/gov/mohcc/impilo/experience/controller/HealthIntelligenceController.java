package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.intelligence.HealthIntelligenceAuthorizationService;
import zw.gov.mohcc.impilo.experience.intelligence.HealthIntelligenceService;
import zw.gov.mohcc.impilo.experience.intelligence.IntelligenceHttpContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health Intelligence plane — cross-service retrieval, summarisation, and decision-support
 * orchestration (Experience BFF). Governed by JWT RBAC and optional Tshepo synthetic authorize.
 */
@RestController
@RequestMapping("/internal/v1/intelligence-plane")
public class HealthIntelligenceController {

    private final HealthIntelligenceService intelligenceService;
    private final HealthIntelligenceAuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    public HealthIntelligenceController(
            HealthIntelligenceService intelligenceService,
            HealthIntelligenceAuthorizationService authorizationService,
            ObjectMapper objectMapper) {
        this.intelligenceService = intelligenceService;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> executeQuery(
            HttpServletRequest httpRequest,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> requestBody) {
        authorizationService.assertIntelligencePlaneAccess("QUERY");
        Map<String, Object> body = new LinkedHashMap<>(requestBody);
        IntelligenceHttpContext.mergeEffectiveVisibility(httpRequest, objectMapper, body);
        return ResponseEntity.ok(
                intelligenceService.executeQuery(body, tenantId, requestId, correlationId, actorId));
    }

    /**
     * Lightweight fused suggestions for shell global search (index + guidance).
     *
     * @param rank_mode optional {@code lexical} | {@code semantic} | {@code hybrid} | {@code recency} (default lexical for fusion UX)
     */
    @GetMapping("/shell-suggest")
    public ResponseEntity<Map<String, Object>> shellSuggest(
            HttpServletRequest httpRequest,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam String q,
            @RequestParam(required = false) String facility_id,
            @RequestParam(name = "rank_mode", required = false) String rank_mode) {
        authorizationService.assertIntelligencePlaneAccess("SHELL_SUGGEST");
        Map<String, Object> vis = IntelligenceHttpContext.visibilityContext(httpRequest, objectMapper);
        return ResponseEntity.ok(
                intelligenceService.shellSuggest(q, facility_id, correlationId, tenantId, vis, rank_mode));
    }
}
