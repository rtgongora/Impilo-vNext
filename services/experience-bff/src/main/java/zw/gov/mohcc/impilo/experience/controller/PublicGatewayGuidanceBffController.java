package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;
import zw.gov.mohcc.impilo.experience.service.PublicGuidanceAskService;

import java.util.Map;

/**
 * Public Nompilo guidance lane for the gateway (no authentication) — thin proxy over the
 * guidance service's {@code PublicGuidanceController} per the public-lane ADR: the only
 * downstream endpoints called live in a service-side {@code Public*Controller}
 * (allow-listed DTOs, no PII, no internal service names, no personalization).
 *
 * <p>Mounted under the single public gateway namespace
 * {@code /internal/v1/public/gateway/**} (permitAll is owned by the gateway lane's
 * SecurityConfig slice — Workstream A; this controller is unreachable until that lands).</p>
 *
 * <ul>
 *   <li>GET /guidance/explain-steps            — active trust-escalation explainers</li>
 *   <li>GET /guidance/explain-steps/{stepKey}  — one explainer (why / level / next / help)</li>
 *   <li>GET /guidance/education                — published public education topics (?domain= / ?category= / ?q= text search)</li>
 *   <li>GET /guidance/education/categories     — published-topic categories with counts</li>
 *   <li>GET /guidance/education/{id}           — one published article incl. plain-language body</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/guidance")
public class PublicGatewayGuidanceBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayGuidanceBffController.class);

    private final GuidanceServiceClient guidanceClient;
    private final PublicGuidanceAskService askService;

    public PublicGatewayGuidanceBffController(GuidanceServiceClient guidanceClient,
                                              PublicGuidanceAskService askService) {
        this.guidanceClient = guidanceClient;
        this.askService = askService;
    }

    /**
     * POST /guidance/ask — anonymous single-turn Nompilo Q&A (rate-limited; grounded +
     * honesty-gated downstream; allow-listed response with a standing disclaimer).
     * 400 = no usable question, 429 = window exhausted (Retry-After), 502 = downstream out
     * with an honest "ask a health worker / call" message left to the UI.
     */
    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Object raw = body == null ? null : body.get("question");
        try {
            return ResponseEntity.ok(askService.ask(raw == null ? null : String.valueOf(raw), clientIp(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "QUESTION_REQUIRED", "message", e.getMessage())));
        } catch (PublicGuidanceAskService.AskRateLimitedException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                    .body(Map.of("error", Map.of("code", "RATE_LIMITED", "message", e.getMessage())));
        } catch (Exception e) {
            log.warn("Public gateway guidance ask failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/explain-steps")
    public ResponseEntity<JsonNode> listExplainSteps() {
        try {
            return ResponseEntity.ok(guidanceClient.listPublicExplainSteps());
        } catch (Exception e) {
            log.warn("Public gateway explain-steps list failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/explain-steps/{stepKey}")
    public ResponseEntity<JsonNode> explainStep(@PathVariable String stepKey) {
        try {
            return ResponseEntity.ok(guidanceClient.getPublicExplainStep(stepKey));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.warn("Public gateway explain-step failed for {}: {}", stepKey, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/education")
    public ResponseEntity<JsonNode> education(
            @RequestParam(defaultValue = "all") String domain,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(required = false, name = "q") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(guidanceClient.getPublicEducation(domain, category, q, page, size));
        } catch (Exception e) {
            log.warn("Public gateway education read failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/education/categories")
    public ResponseEntity<JsonNode> educationCategories() {
        try {
            return ResponseEntity.ok(guidanceClient.getPublicEducationCategories());
        } catch (Exception e) {
            log.warn("Public gateway education categories read failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/education/{id}")
    public ResponseEntity<JsonNode> educationArticle(@PathVariable String id) {
        try {
            return ResponseEntity.ok(guidanceClient.getPublicEducationArticle(id));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.warn("Public gateway education article read failed for {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
