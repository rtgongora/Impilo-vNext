package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;

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
 *   <li>GET /guidance/education                — published public education topics</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/guidance")
public class PublicGatewayGuidanceBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayGuidanceBffController.class);

    private final GuidanceServiceClient guidanceClient;

    public PublicGatewayGuidanceBffController(GuidanceServiceClient guidanceClient) {
        this.guidanceClient = guidanceClient;
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(guidanceClient.getPublicEducation(domain, page, size));
        } catch (Exception e) {
            log.warn("Public gateway education read failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
